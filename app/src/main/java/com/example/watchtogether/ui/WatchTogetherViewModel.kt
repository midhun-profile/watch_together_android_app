package com.example.watchtogether.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.player.VideoPlayer
import com.example.watchtogether.model.ConnectionState
import com.example.watchtogether.model.RoomRole
import com.example.watchtogether.model.RoomState
import com.example.watchtogether.model.RoomUiState
import com.example.watchtogether.model.SyncUiState
import com.example.watchtogether.playback.ExistingPlayerAdapter
import com.example.watchtogether.room.RoomRepository
import com.example.watchtogether.signaling.SignalingClient
import com.example.watchtogether.signaling.SignalingListener
import com.example.watchtogether.signaling.SignalingMessage
import com.example.watchtogether.sync.PlaybackSyncManager
import com.example.watchtogether.webrtc.WebRtcListener
import com.example.watchtogether.webrtc.WebRtcManager
import com.example.watchtogether.webrtc.WebRtcManagerImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WatchTogetherViewModel(
    application: Application,
    private val roomRepository: RoomRepository,
    val signalingClient: SignalingClient,
    val videoPlayer: VideoPlayer
) : AndroidViewModel(application), SignalingListener, WebRtcListener {

    companion object {
        private const val TAG = "WatchTogetherVM"
    }

    private val playerAdapter = ExistingPlayerAdapter(videoPlayer)
    val syncManager = PlaybackSyncManager(signalingClient, playerAdapter)
    val webRtcManager: WebRtcManager = WebRtcManagerImpl(application, signalingClient)

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    val syncState: StateFlow<SyncUiState> = syncManager.syncState

    init {
        signalingClient.addListener(this)
        webRtcManager.setListener(this)

        viewModelScope.launch {
            signalingClient.connectionState.collect { connState ->
                _uiState.update { it.copy(connectionState = connState) }
            }
        }
    }

    fun createRoom() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    roomState = RoomState.WAITING,
                    connectionState = ConnectionState.CONNECTING,
                    role = RoomRole.HOST,
                    errorMessage = null
                )
            }

            val result = roomRepository.createRoom()
            result.onSuccess { response ->
                val code = response.roomCode
                _uiState.update {
                    it.copy(
                        roomCode = code,
                        role = RoomRole.HOST,
                        roomState = RoomState.WAITING
                    )
                }
                // Connect WebSocket as HOST
                signalingClient.connect(code, RoomRole.HOST)
                syncManager.start(code, RoomRole.HOST)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        roomState = RoomState.IDLE,
                        connectionState = ConnectionState.FAILED,
                        errorMessage = error.localizedMessage ?: "Failed to create room"
                    )
                }
            }
        }
    }

    fun joinRoom(code: String) {
        val normalized = code.trim().uppercase()
        if (normalized.length != 6) {
            _uiState.update { it.copy(errorMessage = "Enter a valid 6-character room code") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    roomState = RoomState.WAITING,
                    connectionState = ConnectionState.CONNECTING,
                    role = RoomRole.VIEWER,
                    errorMessage = null
                )
            }

            val result = roomRepository.joinRoom(normalized)
            result.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        roomCode = normalized,
                        role = RoomRole.VIEWER,
                        roomState = RoomState.WAITING
                    )
                }
                // Connect WebSocket as VIEWER
                signalingClient.connect(normalized, RoomRole.VIEWER)
                syncManager.start(normalized, RoomRole.VIEWER)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        roomState = RoomState.IDLE,
                        connectionState = ConnectionState.FAILED,
                        errorMessage = error.localizedMessage ?: "Failed to join room"
                    )
                }
            }
        }
    }

    fun leaveRoom() {
        val code = _uiState.value.roomCode
        syncManager.stop()
        webRtcManager.close()
        signalingClient.disconnect()

        if (code != null) {
            viewModelScope.launch {
                roomRepository.leaveRoom(code)
            }
        }

        _uiState.update {
            RoomUiState(
                roomState = RoomState.IDLE,
                connectionState = ConnectionState.DISCONNECTED
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Host Media Notification
    fun notifyMediaSelected(title: String, durationMs: Long) {
        _uiState.update {
            it.copy(
                mediaTitle = title,
                mediaDurationMs = durationMs,
                roomState = RoomState.ACTIVE
            )
        }
        syncManager.onHostMediaStarted(title, durationMs)
        webRtcManager.setVideoSource("Local Media: $title")
    }

    // SignalingListener Implementation
    override fun onConnected() {
        Log.d(TAG, "Signaling connected for ${_uiState.value.roomCode}")
        val role = _uiState.value.role
        val roomCode = _uiState.value.roomCode ?: return

        // If host, prepare WebRTC offer for viewer
        if (role == RoomRole.HOST && _uiState.value.participantConnected) {
            webRtcManager.createOffer(roomCode)
        }
    }

    override fun onDisconnected(reason: String) {
        Log.d(TAG, "Signaling disconnected: $reason")
    }

    override fun onMessageReceived(message: SignalingMessage) {
        val roomCode = _uiState.value.roomCode ?: return

        when (message.type) {
            SignalingMessage.TYPE_ROOM_JOINED -> {
                val participants = message.payload.optInt("participantCount", 1)
                _uiState.update {
                    it.copy(
                        participantConnected = participants >= 2,
                        roomState = if (participants >= 2) RoomState.CONNECTED else RoomState.WAITING
                    )
                }
                if (participants >= 2 && _uiState.value.role == RoomRole.HOST) {
                    webRtcManager.createOffer(roomCode)
                }
            }

            SignalingMessage.TYPE_USER_JOINED -> {
                Log.d(TAG, "Peer joined the room!")
                _uiState.update {
                    it.copy(
                        participantConnected = true,
                        roomState = RoomState.CONNECTED
                    )
                }
                if (_uiState.value.role == RoomRole.HOST) {
                    webRtcManager.createOffer(roomCode)
                }
            }

            SignalingMessage.TYPE_USER_LEFT -> {
                Log.d(TAG, "Peer left the room")
                _uiState.update {
                    it.copy(
                        participantConnected = false,
                        roomState = RoomState.WAITING
                    )
                }
            }

            SignalingMessage.TYPE_HOST_DISCONNECTED -> {
                Log.d(TAG, "Host ended the session")
                _uiState.update {
                    it.copy(
                        participantConnected = false,
                        roomState = RoomState.DISCONNECTED,
                        errorMessage = "Host ended the WatchTogether session."
                    )
                }
            }

            SignalingMessage.TYPE_WEBRTC_OFFER -> {
                val sdp = message.payload.optString("sdp")
                webRtcManager.setRemoteDescription("offer", sdp)
                webRtcManager.createAnswer(roomCode)
            }

            SignalingMessage.TYPE_WEBRTC_ANSWER -> {
                val sdp = message.payload.optString("sdp")
                webRtcManager.setRemoteDescription("answer", sdp)
            }

            SignalingMessage.TYPE_ICE_CANDIDATE -> {
                val candidate = message.payload.optString("candidate")
                val sdpMid = message.payload.optString("sdpMid")
                val sdpMLineIndex = message.payload.optInt("sdpMLineIndex", 0)
                webRtcManager.addIceCandidate(candidate, sdpMid, sdpMLineIndex)
            }

            SignalingMessage.TYPE_PLAY,
            SignalingMessage.TYPE_PAUSE,
            SignalingMessage.TYPE_SEEK,
            SignalingMessage.TYPE_SYNC,
            SignalingMessage.TYPE_MEDIA_STARTED -> {
                syncManager.handleIncomingSignaling(message)
                if (message.type == SignalingMessage.TYPE_MEDIA_STARTED) {
                    val name = message.payload.optString("name")
                    val duration = message.payload.optLong("durationMs", 0L)
                    _uiState.update {
                        it.copy(
                            mediaTitle = name,
                            mediaDurationMs = duration,
                            roomState = RoomState.ACTIVE
                        )
                    }
                }
            }

            SignalingMessage.TYPE_ERROR -> {
                val err = message.payload.optString("message", "Error from server")
                _uiState.update { it.copy(errorMessage = err) }
            }
        }
    }

    override fun onError(error: String) {
        _uiState.update { it.copy(errorMessage = error) }
    }

    // WebRtcListener Implementation
    override fun onIceConnectionState(state: String) {
        Log.d(TAG, "ICE State: $state")
    }

    override fun onPeerConnected() {
        Log.d(TAG, "WebRTC Peer successfully connected P2P!")
    }

    override fun onPeerDisconnected() {
        Log.d(TAG, "WebRTC Peer disconnected")
    }

    override fun onCleared() {
        super.onCleared()
        signalingClient.removeListener(this)
        webRtcManager.setListener(null)
        leaveRoom()
    }
}
