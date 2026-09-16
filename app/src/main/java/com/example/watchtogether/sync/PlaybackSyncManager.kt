package com.example.watchtogether.sync

import android.util.Log
import com.example.watchtogether.model.RoomRole
import com.example.watchtogether.model.SyncUiState
import com.example.watchtogether.model.VideoReadinessState
import com.example.watchtogether.playback.PlaybackController
import com.example.watchtogether.signaling.SignalingClient
import com.example.watchtogether.signaling.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SharedPlaybackState(
    val initialized: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val sequence: Long = 0L,
    val updatedAt: Long = 0L
)

class PlaybackSyncManager(
    private val signalingClient: SignalingClient,
    private val playbackController: PlaybackController
) {
    companion object {
        private const val TAG = "PlaybackSyncManager"
        private const val PERIODIC_SYNC_INTERVAL_MS = 1500L
        private const val DRIFT_IGNORE_THRESHOLD_MS = 250L
        private const val DRIFT_SEEK_THRESHOLD_MS = 1000L
        private const val REMOTE_GUARD_DELAY_MS = 400L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var periodicSyncJob: Job? = null

    private var activeRoomCode: String = ""
    private var activeRole: RoomRole = RoomRole.HOST
    private var lastProcessedSequence: Long = 0L

    // Remote-update guard to prevent synchronization feedback loops
    @Volatile
    var isRemoteUpdate: Boolean = false
        private set

    // Initialization flag: distinguishes video loading events from explicit user actions
    @Volatile
    var isInitializingVideo: Boolean = false
        private set

    // Video readiness state
    @Volatile
    var videoReadiness: VideoReadinessState = VideoReadinessState.NO_SOURCE
        private set

    var sharedPlaybackState: SharedPlaybackState = SharedPlaybackState()
        private set

    private var pendingPlaybackState: SignalingMessage? = null

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _requiresUserInteraction = MutableStateFlow(false)
    val requiresUserInteraction: StateFlow<Boolean> = _requiresUserInteraction.asStateFlow()

    fun isVideoReady(): Boolean {
        if (playbackController.getDuration() > 0 || playbackController.getCurrentPosition() > 0 || playbackController.isPlaying()) {
            return true
        }
        return videoReadiness == VideoReadinessState.VIDEO_READY ||
                videoReadiness == VideoReadinessState.PLAYING ||
                videoReadiness == VideoReadinessState.PAUSED ||
                videoReadiness == VideoReadinessState.SYNCING
    }

    fun start(roomCode: String, role: RoomRole) {
        activeRoomCode = roomCode
        activeRole = role
        lastProcessedSequence = 0L
        isRemoteUpdate = false
        isInitializingVideo = false
        sharedPlaybackState = SharedPlaybackState()
        pendingPlaybackState = null
        _requiresUserInteraction.value = false

        if (role == RoomRole.HOST) {
            startPeriodicSyncBroadcast()
        }
    }

    fun stop() {
        periodicSyncJob?.cancel()
        periodicSyncJob = null
        isRemoteUpdate = false
        isInitializingVideo = false
        videoReadiness = VideoReadinessState.NO_SOURCE
        sharedPlaybackState = SharedPlaybackState()
        pendingPlaybackState = null
        _requiresUserInteraction.value = false
    }

    fun setVideoLoading() {
        videoReadiness = VideoReadinessState.VIDEO_LOADING
        isInitializingVideo = true
        _requiresUserInteraction.value = false
        Log.d(TAG, "[$activeRole] videoReadiness set to VIDEO_LOADING, isInitializingVideo=true")
    }

    fun onVideoReady() {
        Log.d(TAG, "[$activeRole] videoReadiness transition to VIDEO_READY")
        Log.d("[VIDEO]", "Viewer VIDEO_READY")
        videoReadiness = VideoReadinessState.VIDEO_READY

        // When viewer reaches VIDEO_READY, request the current playback state from the host/room
        if (activeRole == RoomRole.VIEWER && activeRoomCode.isNotEmpty()) {
            Log.d("[SYNC]", "Playback state requested")
            val reqStateMsg = SignalingMessage.createRequestPlaybackState(activeRoomCode)
            signalingClient.send(reqStateMsg)
            val reqSyncMsg = SignalingMessage.createRequestSync(activeRoomCode)
            signalingClient.send(reqSyncMsg)
        }

        // If there was a queued playback command while video was loading, check if initialized
        val pending = pendingPlaybackState
        if (pending != null) {
            pendingPlaybackState = null
            val isInit = pending.payload.optBoolean("initialized", false)
            val isExplicitCommand = pending.type == SignalingMessage.TYPE_PLAY ||
                    pending.type == SignalingMessage.TYPE_PAUSE ||
                    pending.type == SignalingMessage.TYPE_SEEK

            if (isInit || isExplicitCommand) {
                Log.d(TAG, "[$activeRole] Applying pending playback command: ${pending.type}")
                if (pending.type == SignalingMessage.TYPE_PLAYBACK_STATE) {
                    applyRemotePlaybackState(pending)
                } else if (pending.type == SignalingMessage.TYPE_SYNC) {
                    applyRemoteSyncCommand(pending)
                } else {
                    applyRemotePlaybackCommand(pending)
                }
            } else {
                Log.d(TAG, "[$activeRole] Discarding uninitialized pending ${pending.type}")
            }
        } else {
            // No pending state yet - wait for shared playback state response
            isInitializingVideo = false
        }
    }

    fun requestSync() {
        if (activeRoomCode.isEmpty()) return
        Log.d(TAG, "[$activeRole] Requesting latest sync state for room $activeRoomCode")
        Log.d("[SYNC]", "Playback state requested")
        signalingClient.send(SignalingMessage.createRequestPlaybackState(activeRoomCode))
        signalingClient.send(SignalingMessage.createRequestSync(activeRoomCode))
    }

    // Bidirectional Local Controls (Both Host and Viewer)
    fun onLocalPlay() {
        if (isRemoteUpdate) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed play broadcast: isRemoteUpdate=true")
            return
        }
        if (isInitializingVideo) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed play broadcast: isInitializingVideo=true")
            return
        }
        if (!isVideoReady()) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed play broadcast: video not ready")
            return
        }
        _requiresUserInteraction.value = false
        Log.d("[SYNC]", "Local PLAY")
        val pos = playbackController.getCurrentPosition()
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createPlay(activeRoomCode, seq, pos)
        signalingClient.send(msg)

        sharedPlaybackState = SharedPlaybackState(
            initialized = true,
            isPlaying = true,
            positionMs = pos,
            playbackSpeed = 1.0f,
            sequence = seq,
            updatedAt = System.currentTimeMillis()
        )
        videoReadiness = VideoReadinessState.PLAYING
        _syncState.update { it.copy(currentPositionMs = pos, isPlaying = true, lastSequence = seq) }
        Log.d(TAG, "[$activeRole LOCAL PLAY] seq=$seq pos=$pos")
    }

    fun onLocalPause() {
        if (isRemoteUpdate) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed pause broadcast: isRemoteUpdate=true")
            return
        }
        if (isInitializingVideo) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed pause broadcast: isInitializingVideo=true")
            return
        }
        if (!isVideoReady()) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed pause broadcast: video not ready")
            return
        }
        _requiresUserInteraction.value = false
        Log.d("[SYNC]", "Local PAUSE")
        val pos = playbackController.getCurrentPosition()
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createPause(activeRoomCode, seq, pos)
        signalingClient.send(msg)

        sharedPlaybackState = SharedPlaybackState(
            initialized = true,
            isPlaying = false,
            positionMs = pos,
            playbackSpeed = 1.0f,
            sequence = seq,
            updatedAt = System.currentTimeMillis()
        )
        videoReadiness = VideoReadinessState.PAUSED
        _syncState.update { it.copy(currentPositionMs = pos, isPlaying = false, lastSequence = seq) }
        Log.d(TAG, "[$activeRole LOCAL PAUSE] seq=$seq pos=$pos")
    }

    fun onLocalSeek(targetPosMs: Long) {
        if (isRemoteUpdate) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed seek broadcast: isRemoteUpdate=true")
            return
        }
        if (isInitializingVideo) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed seek broadcast: isInitializingVideo=true")
            return
        }
        if (!isVideoReady()) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed seek broadcast: video not ready")
            return
        }
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createSeek(activeRoomCode, seq, targetPosMs)
        signalingClient.send(msg)
        sharedPlaybackState = sharedPlaybackState.copy(
            positionMs = targetPosMs,
            sequence = seq,
            updatedAt = System.currentTimeMillis()
        )
        _syncState.update { it.copy(currentPositionMs = targetPosMs, lastSequence = seq) }
        Log.d(TAG, "[$activeRole LOCAL SEEK] seq=$seq target=$targetPosMs")
    }

    // Host Only: Selecting movie
    fun onHostMediaStarted(name: String, durationMs: Long, uri: String? = null) {
        if (activeRole != RoomRole.HOST) return
        videoReadiness = VideoReadinessState.VIDEO_READY
        isInitializingVideo = false
        val msg = SignalingMessage.createMediaStarted(activeRoomCode, name, durationMs, uri)
        signalingClient.send(msg)
        Log.d(TAG, "[HOST MEDIA STARTED] name=$name duration=$durationMs uri=$uri")
    }

    // Backwards compatibility aliases
    fun onHostPlay() = onLocalPlay()
    fun onHostPause() = onLocalPause()
    fun onHostSeek(targetPosMs: Long) = onLocalSeek(targetPosMs)
    fun onHostMediaStarted(name: String, durationMs: Long) = onHostMediaStarted(name, durationMs, null)

    private fun startPeriodicSyncBroadcast() {
        periodicSyncJob?.cancel()
        periodicSyncJob = scope.launch {
            while (isActive) {
                delay(PERIODIC_SYNC_INTERVAL_MS)
                if (activeRole == RoomRole.HOST && activeRoomCode.isNotEmpty() && isVideoReady()) {
                    val pos = playbackController.getCurrentPosition()
                    val isPlaying = playbackController.isPlaying()
                    val seq = signalingClient.nextSequence()
                    val isInit = sharedPlaybackState.initialized || isPlaying || pos > 0L
                    val syncMsg = SignalingMessage.createSync(
                        roomCode = activeRoomCode,
                        sequence = seq,
                        positionMs = pos,
                        isPlaying = isPlaying,
                        playbackSpeed = 1.0f,
                        initialized = isInit
                    )
                    signalingClient.send(syncMsg)
                    _syncState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = playbackController.getDuration(),
                            isPlaying = isPlaying,
                            lastSequence = seq,
                            isInSync = true,
                            driftMs = 0L
                        )
                    }
                }
            }
        }
    }

    // Process incoming signaling messages
    fun handleIncomingSignaling(message: SignalingMessage) {
        val seq = message.sequence

        // Stale sequence check
        if (seq > 0 && seq <= lastProcessedSequence) {
            Log.d(TAG, "Ignoring stale sequence $seq <= $lastProcessedSequence")
            return
        }
        if (seq > 0) {
            lastProcessedSequence = seq
        }

        when (message.type) {
            SignalingMessage.TYPE_REQUEST_PLAYBACK_STATE,
            SignalingMessage.TYPE_REQUEST_SYNC -> {
                // If receiving request for playback state, reply immediately if video is ready
                if (isVideoReady()) {
                    val pos = playbackController.getCurrentPosition()
                    val isPlaying = playbackController.isPlaying()
                    val s = signalingClient.nextSequence()
                    val isInit = sharedPlaybackState.initialized || isPlaying || pos > 0L
                    val response = SignalingMessage.createPlaybackState(
                        roomCode = activeRoomCode,
                        sequence = s,
                        positionMs = pos,
                        isPlaying = isPlaying,
                        playbackSpeed = 1.0f,
                        initialized = isInit
                    )
                    signalingClient.send(response)
                    Log.d(TAG, "[$activeRole] Responded to ${message.type}: pos=$pos isPlaying=$isPlaying init=$isInit")
                }
            }

            SignalingMessage.TYPE_PLAYBACK_STATE -> {
                Log.d("[SYNC]", "Playback state received")
                val isInit = message.payload.optBoolean("initialized", true)
                if (!isInit) {
                    Log.d(TAG, "[$activeRole] Received uninitialized PLAYBACK_STATE; ignoring until initialized")
                    return
                }
                if (!isVideoReady()) {
                    Log.d(TAG, "[$activeRole] Queued PLAYBACK_STATE because video is not ready yet")
                    pendingPlaybackState = message
                    return
                }
                applyRemotePlaybackState(message)
            }

            SignalingMessage.TYPE_PLAY,
            SignalingMessage.TYPE_PAUSE,
            SignalingMessage.TYPE_SEEK -> {
                if (message.type == SignalingMessage.TYPE_PLAY) {
                    Log.d("[SYNC]", "Received PLAY")
                } else if (message.type == SignalingMessage.TYPE_PAUSE) {
                    Log.d("[SYNC]", "Received PAUSE")
                }

                if (!isVideoReady()) {
                    Log.d(TAG, "[$activeRole] Queued ${message.type} because video is not ready yet")
                    pendingPlaybackState = message
                    return
                }
                applyRemotePlaybackCommand(message)
            }

            SignalingMessage.TYPE_SYNC -> {
                val isInit = if (message.payload.has("initialized")) {
                    message.payload.optBoolean("initialized", false)
                } else {
                    message.payload.optBoolean("isPlaying", false) || message.payload.optLong("positionMs", 0L) > 0L || sharedPlaybackState.initialized
                }
                // Only Viewer applies periodic SYNC from Host
                if (activeRole == RoomRole.VIEWER) {
                    if (!isVideoReady()) {
                        if (isInit) {
                            pendingPlaybackState = message
                        }
                        return
                    }
                    if (isInit || sharedPlaybackState.initialized) {
                        applyRemoteSyncCommand(message)
                    } else {
                        Log.d(TAG, "[$activeRole] Ignored uninitialized SYNC before playback state established")
                    }
                }
            }

            SignalingMessage.TYPE_MEDIA_STARTED -> {
                val name = message.payload.optString("name", "Movie")
                val duration = message.payload.optLong("durationMs", 0L)
                _syncState.update { it.copy(durationMs = duration) }
                if (activeRole == RoomRole.VIEWER) {
                    setVideoLoading()
                }
                Log.d(TAG, "[$activeRole MEDIA_STARTED] $name ($duration ms)")
            }
        }
    }

    private fun applyRemotePlaybackState(message: SignalingMessage) {
        val payload = message.payload
        val isInitialized = payload.optBoolean("initialized", true)
        if (!isInitialized) return

        val now = System.currentTimeMillis()
        val sentAt = payload.optLong("sentAt", now)
        val latency = (now - sentAt).coerceIn(0L, 2000L)
        val seq = message.sequence

        val hostPos = if (payload.has("positionMs")) {
            payload.optLong("positionMs", 0L)
        } else {
            (payload.optDouble("currentTime", 0.0) * 1000).toLong()
        }

        val isPlaying = if (payload.has("isPlaying")) {
            payload.optBoolean("isPlaying", false)
        } else if (payload.has("playing")) {
            payload.optBoolean("playing", false)
        } else if (payload.has("paused")) {
            !payload.optBoolean("paused", true)
        } else {
            false
        }
        val speed = payload.optDouble("playbackSpeed", 1.0).toFloat()

        sharedPlaybackState = SharedPlaybackState(
            initialized = true,
            isPlaying = isPlaying,
            positionMs = hostPos,
            playbackSpeed = speed,
            sequence = seq,
            updatedAt = now
        )

        val targetPos = if (isPlaying) (hostPos + (latency * speed).toLong()) else hostPos

        isRemoteUpdate = true
        try {
            playbackController.seekTo(targetPos)
            if (isPlaying) {
                try {
                    playbackController.play()
                    Log.d("[VIDEO]", "play() succeeded")
                    _requiresUserInteraction.value = false
                } catch (e: Exception) {
                    Log.e("[VIDEO]", "play() rejected", e)
                    _requiresUserInteraction.value = true
                }
                videoReadiness = VideoReadinessState.PLAYING
            } else {
                playbackController.pause()
                videoReadiness = VideoReadinessState.PAUSED
            }

            _syncState.update {
                it.copy(
                    currentPositionMs = targetPos,
                    durationMs = playbackController.getDuration(),
                    isPlaying = isPlaying,
                    playbackSpeed = speed,
                    isInSync = true,
                    lastSequence = seq
                )
            }
        } finally {
            isInitializingVideo = false
            scope.launch {
                delay(REMOTE_GUARD_DELAY_MS)
                isRemoteUpdate = false
            }
        }
    }

    private fun applyRemotePlaybackCommand(message: SignalingMessage) {
        val payload = message.payload
        val now = System.currentTimeMillis()
        val sentAt = payload.optLong("sentAt", now)
        val latency = (now - sentAt).coerceIn(0L, 2000L)
        val seq = message.sequence

        isRemoteUpdate = true
        try {
            when (message.type) {
                SignalingMessage.TYPE_PLAY -> {
                    Log.d("[SYNC]", "Remote PLAY")
                    val remotePos = payload.optLong("positionMs", 0L)
                    val targetPos = remotePos + latency
                    playbackController.seekTo(targetPos)
                    try {
                        playbackController.play()
                        Log.d("[VIDEO]", "play() succeeded")
                        _requiresUserInteraction.value = false
                    } catch (e: Exception) {
                        Log.e("[VIDEO]", "play() rejected", e)
                        _requiresUserInteraction.value = true
                    }
                    videoReadiness = VideoReadinessState.PLAYING
                    sharedPlaybackState = SharedPlaybackState(
                        initialized = true,
                        isPlaying = true,
                        positionMs = targetPos,
                        sequence = seq,
                        updatedAt = now
                    )
                    _syncState.update {
                        it.copy(
                            currentPositionMs = targetPos,
                            isPlaying = true,
                            lastSequence = seq,
                            isInSync = true
                        )
                    }
                    Log.d(TAG, "[$activeRole REMOTE PLAY] applied targetPos=$targetPos (latency=${latency}ms)")
                }

                SignalingMessage.TYPE_PAUSE -> {
                    Log.d("[SYNC]", "Remote PAUSE")
                    val remotePos = payload.optLong("positionMs", 0L)
                    playbackController.seekTo(remotePos)
                    playbackController.pause()
                    videoReadiness = VideoReadinessState.PAUSED
                    sharedPlaybackState = SharedPlaybackState(
                        initialized = true,
                        isPlaying = false,
                        positionMs = remotePos,
                        sequence = seq,
                        updatedAt = now
                    )
                    _syncState.update {
                        it.copy(
                            currentPositionMs = remotePos,
                            isPlaying = false,
                            lastSequence = seq,
                            isInSync = true
                        )
                    }
                    Log.d(TAG, "[$activeRole REMOTE PAUSE] applied remotePos=$remotePos")
                }

                SignalingMessage.TYPE_SEEK -> {
                    Log.d("[SYNC]", "Remote SEEK")
                    val targetPos = payload.optLong("targetPositionMs", 0L)
                    playbackController.seekTo(targetPos)
                    sharedPlaybackState = sharedPlaybackState.copy(
                        positionMs = targetPos,
                        sequence = seq,
                        updatedAt = now
                    )
                    _syncState.update {
                        it.copy(
                            currentPositionMs = targetPos,
                            lastSequence = seq,
                            isInSync = true
                        )
                    }
                    Log.d(TAG, "[$activeRole REMOTE SEEK] applied targetPos=$targetPos")
                }

                SignalingMessage.TYPE_SYNC -> {
                    applyRemoteSyncCommand(message)
                }
            }
        } finally {
            isInitializingVideo = false
            scope.launch {
                delay(REMOTE_GUARD_DELAY_MS)
                isRemoteUpdate = false
            }
        }
    }

    private fun applyRemoteSyncCommand(message: SignalingMessage) {
        val payload = message.payload
        val isInitialized = if (payload.has("initialized")) {
            payload.optBoolean("initialized", false)
        } else {
            payload.optBoolean("isPlaying", false) || payload.optLong("positionMs", 0L) > 0L || sharedPlaybackState.initialized
        }

        // Do not treat playing: false as an explicit pause until state is initialized
        if (!isInitialized && !sharedPlaybackState.initialized) {
            Log.d(TAG, "[$activeRole] Ignored uninitialized SYNC command")
            return
        }

        val now = System.currentTimeMillis()
        val sentAt = payload.optLong("sentAt", now)
        val latency = (now - sentAt).coerceIn(0L, 2000L)
        val seq = message.sequence

        val hostPos = payload.optLong("positionMs", 0L)
        val isPlaying = payload.optBoolean("isPlaying", false)
        val speed = payload.optDouble("playbackSpeed", 1.0).toFloat()

        sharedPlaybackState = SharedPlaybackState(
            initialized = true,
            isPlaying = isPlaying,
            positionMs = hostPos,
            playbackSpeed = speed,
            sequence = seq,
            updatedAt = now
        )

        val targetPos = if (isPlaying) (hostPos + (latency * speed).toLong()) else hostPos
        val currentViewerPos = playbackController.getCurrentPosition()
        val drift = abs(currentViewerPos - targetPos)
        val inSync = drift <= DRIFT_IGNORE_THRESHOLD_MS

        isRemoteUpdate = true
        try {
            if (drift > DRIFT_SEEK_THRESHOLD_MS) {
                Log.d(TAG, "[VIEWER SYNC] Hard correcting drift=${drift}ms > threshold, seekTo $targetPos")
                playbackController.seekTo(targetPos)
            } else if (drift > DRIFT_IGNORE_THRESHOLD_MS) {
                Log.d(TAG, "[VIEWER SYNC] Minor drift ${drift}ms within gentle range")
            }

            if (isPlaying != playbackController.isPlaying()) {
                if (isPlaying) {
                    try {
                        playbackController.play()
                        Log.d("[VIDEO]", "play() succeeded")
                        _requiresUserInteraction.value = false
                    } catch (e: Exception) {
                        Log.e("[VIDEO]", "play() rejected", e)
                        _requiresUserInteraction.value = true
                    }
                    videoReadiness = VideoReadinessState.PLAYING
                } else {
                    playbackController.pause()
                    videoReadiness = VideoReadinessState.PAUSED
                }
            }

            _syncState.update {
                it.copy(
                    currentPositionMs = currentViewerPos,
                    durationMs = playbackController.getDuration(),
                    isPlaying = isPlaying,
                    playbackSpeed = speed,
                    driftMs = drift,
                    isInSync = inSync,
                    lastSequence = seq
                )
            }
        } finally {
            isInitializingVideo = false
            scope.launch {
                delay(REMOTE_GUARD_DELAY_MS)
                isRemoteUpdate = false
            }
        }
    }
}
