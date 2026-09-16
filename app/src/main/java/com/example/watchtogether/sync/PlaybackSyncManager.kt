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

    // Remote-update guard to prevent synchronization loops
    @Volatile
    var isRemoteUpdate: Boolean = false
        private set

    // Video readiness state
    @Volatile
    var videoReadiness: VideoReadinessState = VideoReadinessState.NO_SOURCE
        private set

    private var pendingPlaybackState: SignalingMessage? = null

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

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
        pendingPlaybackState = null

        if (role == RoomRole.HOST) {
            startPeriodicSyncBroadcast()
        }
    }

    fun stop() {
        periodicSyncJob?.cancel()
        periodicSyncJob = null
        isRemoteUpdate = false
        videoReadiness = VideoReadinessState.NO_SOURCE
        pendingPlaybackState = null
    }

    fun setVideoLoading() {
        videoReadiness = VideoReadinessState.VIDEO_LOADING
        Log.d(TAG, "[$activeRole] videoReadiness set to VIDEO_LOADING")
    }

    fun onVideoReady() {
        Log.d(TAG, "[$activeRole] videoReadiness transition to VIDEO_READY")
        videoReadiness = VideoReadinessState.VIDEO_READY

        // If there was a queued playback command while video was loading, apply it now
        val pending = pendingPlaybackState
        if (pending != null) {
            pendingPlaybackState = null
            Log.d(TAG, "[$activeRole] Applying pending playback command: ${pending.type}")
            if (pending.type == SignalingMessage.TYPE_SYNC) {
                applyRemoteSyncCommand(pending)
            } else {
                applyRemotePlaybackCommand(pending)
            }
        } else if (activeRole == RoomRole.VIEWER && activeRoomCode.isNotEmpty()) {
            // Request the latest shared state upon video readiness
            requestSync()
        }
    }

    fun requestSync() {
        if (activeRoomCode.isEmpty()) return
        Log.d(TAG, "[$activeRole] Requesting latest sync state for room $activeRoomCode")
        val msg = SignalingMessage.createRequestSync(activeRoomCode)
        signalingClient.send(msg)
    }

    // Bidirectional Local Controls (Both Host and Viewer)
    fun onLocalPlay() {
        if (isRemoteUpdate) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed play broadcast: isRemoteUpdate=true")
            return
        }
        if (!isVideoReady()) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed play broadcast: video not ready")
            return
        }
        val pos = playbackController.getCurrentPosition()
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createPlay(activeRoomCode, seq, pos)
        signalingClient.send(msg)
        videoReadiness = VideoReadinessState.PLAYING
        _syncState.update { it.copy(currentPositionMs = pos, isPlaying = true, lastSequence = seq) }
        Log.d(TAG, "[$activeRole LOCAL PLAY] seq=$seq pos=$pos")
    }

    fun onLocalPause() {
        if (isRemoteUpdate) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed pause broadcast: isRemoteUpdate=true")
            return
        }
        if (!isVideoReady()) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed pause broadcast: video not ready")
            return
        }
        val pos = playbackController.getCurrentPosition()
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createPause(activeRoomCode, seq, pos)
        signalingClient.send(msg)
        videoReadiness = VideoReadinessState.PAUSED
        _syncState.update { it.copy(currentPositionMs = pos, isPlaying = false, lastSequence = seq) }
        Log.d(TAG, "[$activeRole LOCAL PAUSE] seq=$seq pos=$pos")
    }

    fun onLocalSeek(targetPosMs: Long) {
        if (isRemoteUpdate) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed seek broadcast: isRemoteUpdate=true")
            return
        }
        if (!isVideoReady()) {
            Log.d(TAG, "[$activeRole GUARD] Suppressed seek broadcast: video not ready")
            return
        }
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createSeek(activeRoomCode, seq, targetPosMs)
        signalingClient.send(msg)
        _syncState.update { it.copy(currentPositionMs = targetPosMs, lastSequence = seq) }
        Log.d(TAG, "[$activeRole LOCAL SEEK] seq=$seq target=$targetPosMs")
    }

    // Host Only: Selecting movie
    fun onHostMediaStarted(name: String, durationMs: Long, uri: String? = null) {
        if (activeRole != RoomRole.HOST) return
        videoReadiness = VideoReadinessState.VIDEO_READY
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
                    val syncMsg = SignalingMessage.createSync(
                        roomCode = activeRoomCode,
                        sequence = seq,
                        positionMs = pos,
                        isPlaying = isPlaying,
                        playbackSpeed = 1.0f
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
            SignalingMessage.TYPE_REQUEST_SYNC -> {
                // If Host receives request for sync, reply immediately with current state
                if (activeRole == RoomRole.HOST && isVideoReady()) {
                    val pos = playbackController.getCurrentPosition()
                    val isPlaying = playbackController.isPlaying()
                    val s = signalingClient.nextSequence()
                    val syncMsg = SignalingMessage.createSync(
                        roomCode = activeRoomCode,
                        sequence = s,
                        positionMs = pos,
                        isPlaying = isPlaying,
                        playbackSpeed = 1.0f
                    )
                    signalingClient.send(syncMsg)
                    Log.d(TAG, "[HOST] Responded to REQUEST_SYNC: pos=$pos isPlaying=$isPlaying")
                }
            }

            SignalingMessage.TYPE_PLAY,
            SignalingMessage.TYPE_PAUSE,
            SignalingMessage.TYPE_SEEK -> {
                if (!isVideoReady()) {
                    Log.d(TAG, "[$activeRole] Queued ${message.type} because video is not ready yet")
                    pendingPlaybackState = message
                    return
                }
                applyRemotePlaybackCommand(message)
            }

            SignalingMessage.TYPE_SYNC -> {
                // Only Viewer applies periodic SYNC from Host
                if (activeRole == RoomRole.VIEWER) {
                    if (!isVideoReady()) {
                        pendingPlaybackState = message
                        return
                    }
                    applyRemoteSyncCommand(message)
                }
            }

            SignalingMessage.TYPE_MEDIA_STARTED -> {
                val name = message.payload.optString("name", "Movie")
                val duration = message.payload.optLong("durationMs", 0L)
                _syncState.update { it.copy(durationMs = duration) }
                if (activeRole == RoomRole.VIEWER) {
                    videoReadiness = VideoReadinessState.VIDEO_LOADING
                }
                Log.d(TAG, "[$activeRole MEDIA_STARTED] $name ($duration ms)")
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
                    val remotePos = payload.optLong("positionMs", 0L)
                    val targetPos = remotePos + latency
                    playbackController.seekTo(targetPos)
                    playbackController.play()
                    videoReadiness = VideoReadinessState.PLAYING
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
                    val remotePos = payload.optLong("positionMs", 0L)
                    playbackController.seekTo(remotePos)
                    playbackController.pause()
                    videoReadiness = VideoReadinessState.PAUSED
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
                    val targetPos = payload.optLong("targetPositionMs", 0L)
                    playbackController.seekTo(targetPos)
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
            scope.launch {
                delay(REMOTE_GUARD_DELAY_MS)
                isRemoteUpdate = false
            }
        }
    }

    private fun applyRemoteSyncCommand(message: SignalingMessage) {
        val payload = message.payload
        val now = System.currentTimeMillis()
        val sentAt = payload.optLong("sentAt", now)
        val latency = (now - sentAt).coerceIn(0L, 2000L)
        val seq = message.sequence

        val hostPos = payload.optLong("positionMs", 0L)
        val isPlaying = payload.optBoolean("isPlaying", false)
        val speed = payload.optDouble("playbackSpeed", 1.0).toFloat()

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
                    playbackController.play()
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
            scope.launch {
                delay(REMOTE_GUARD_DELAY_MS)
                isRemoteUpdate = false
            }
        }
    }
}
