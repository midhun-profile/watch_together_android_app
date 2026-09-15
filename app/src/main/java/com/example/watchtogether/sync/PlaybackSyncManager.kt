package com.example.watchtogether.sync

import android.util.Log
import com.example.watchtogether.model.RoomRole
import com.example.watchtogether.model.SyncUiState
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
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var periodicSyncJob: Job? = null

    private var activeRoomCode: String = ""
    private var activeRole: RoomRole = RoomRole.VIEWER
    private var lastProcessedSequence: Long = 0L

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    fun start(roomCode: String, role: RoomRole) {
        activeRoomCode = roomCode
        activeRole = role
        lastProcessedSequence = 0L

        if (role == RoomRole.HOST) {
            startPeriodicSyncBroadcast()
        }
    }

    fun stop() {
        periodicSyncJob?.cancel()
        periodicSyncJob = null
    }

    // Host Actions (Authoritative)
    fun onHostPlay() {
        if (activeRole != RoomRole.HOST) return
        val pos = playbackController.getCurrentPosition()
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createPlay(activeRoomCode, seq, pos)
        signalingClient.send(msg)
        Log.d(TAG, "[HOST PLAY] seq=$seq pos=$pos")
    }

    fun onHostPause() {
        if (activeRole != RoomRole.HOST) return
        val pos = playbackController.getCurrentPosition()
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createPause(activeRoomCode, seq, pos)
        signalingClient.send(msg)
        Log.d(TAG, "[HOST PAUSE] seq=$seq pos=$pos")
    }

    fun onHostSeek(targetPosMs: Long) {
        if (activeRole != RoomRole.HOST) return
        val seq = signalingClient.nextSequence()
        val msg = SignalingMessage.createSeek(activeRoomCode, seq, targetPosMs)
        signalingClient.send(msg)
        Log.d(TAG, "[HOST SEEK] seq=$seq target=$targetPosMs")
    }

    fun onHostMediaStarted(name: String, durationMs: Long) {
        if (activeRole != RoomRole.HOST) return
        val msg = SignalingMessage.createMediaStarted(activeRoomCode, name, durationMs)
        signalingClient.send(msg)
        Log.d(TAG, "[HOST MEDIA STARTED] name=$name duration=$durationMs")
    }

    private fun startPeriodicSyncBroadcast() {
        periodicSyncJob?.cancel()
        periodicSyncJob = scope.launch {
            while (isActive) {
                delay(PERIODIC_SYNC_INTERVAL_MS)
                if (activeRole == RoomRole.HOST && activeRoomCode.isNotEmpty()) {
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

    // Viewer Sync Handler
    fun handleIncomingSignaling(message: SignalingMessage) {
        val seq = message.sequence

        // Stale sequence check (Section 31)
        if (seq > 0 && seq <= lastProcessedSequence) {
            Log.d(TAG, "Ignoring stale sequence $seq <= $lastProcessedSequence")
            return
        }
        if (seq > 0) {
            lastProcessedSequence = seq
        }

        val now = System.currentTimeMillis()
        val payload = message.payload
        val sentAt = payload.optLong("sentAt", now)
        val latency = (now - sentAt).coerceIn(0L, 2000L) // estimated one-way latency

        when (message.type) {
            SignalingMessage.TYPE_PLAY -> {
                val hostPos = payload.optLong("positionMs", 0L)
                val targetPos = hostPos + latency
                playbackController.seekTo(targetPos)
                playbackController.play()
                _syncState.update {
                    it.copy(
                        currentPositionMs = targetPos,
                        isPlaying = true,
                        lastSequence = seq,
                        isInSync = true
                    )
                }
                Log.d(TAG, "[VIEWER PLAY] synced to $targetPos (latency=${latency}ms)")
            }

            SignalingMessage.TYPE_PAUSE -> {
                val hostPos = payload.optLong("positionMs", 0L)
                playbackController.seekTo(hostPos)
                playbackController.pause()
                _syncState.update {
                    it.copy(
                        currentPositionMs = hostPos,
                        isPlaying = false,
                        lastSequence = seq,
                        isInSync = true
                    )
                }
                Log.d(TAG, "[VIEWER PAUSE] synced to $hostPos")
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
                Log.d(TAG, "[VIEWER SEEK] seekTo $targetPos")
            }

            SignalingMessage.TYPE_SYNC -> {
                val hostPos = payload.optLong("positionMs", 0L)
                val isPlaying = payload.optBoolean("isPlaying", false)
                val speed = payload.optDouble("playbackSpeed", 1.0).toFloat()

                val targetPos = if (isPlaying) (hostPos + (latency * speed).toLong()) else hostPos
                val currentViewerPos = playbackController.getCurrentPosition()
                val drift = abs(currentViewerPos - targetPos)

                val inSync = drift <= DRIFT_IGNORE_THRESHOLD_MS

                if (drift > DRIFT_SEEK_THRESHOLD_MS) {
                    // Significant drift: hard seek to authoritative host position
                    Log.d(TAG, "[VIEWER SYNC] Hard correcting drift=${drift}ms > threshold, seekTo $targetPos")
                    playbackController.seekTo(targetPos)
                } else if (drift > DRIFT_IGNORE_THRESHOLD_MS) {
                    // Small drift: gentle correction
                    Log.d(TAG, "[VIEWER SYNC] Minor drift ${drift}ms within gentle range")
                }

                // Match play state if out of sync
                if (isPlaying != playbackController.isPlaying()) {
                    if (isPlaying) playbackController.play() else playbackController.pause()
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
            }

            SignalingMessage.TYPE_MEDIA_STARTED -> {
                val name = payload.optString("name", "Movie")
                val duration = payload.optLong("durationMs", 0L)
                _syncState.update {
                    it.copy(
                        durationMs = duration
                    )
                }
                Log.d(TAG, "[VIEWER MEDIA_STARTED] $name ($duration ms)")
            }
        }
    }
}
