package com.example.watchtogether.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.player.VideoPlayer
import com.example.watchtogether.model.ConnectionState
import com.example.watchtogether.model.RoomRole
import com.example.watchtogether.model.RoomSession
import com.example.watchtogether.model.RoomState
import com.example.watchtogether.model.RoomUiState
import com.example.watchtogether.model.SyncUiState
import com.example.watchtogether.model.VideoReadinessState
import com.example.watchtogether.playback.ExistingPlayerAdapter
import com.example.watchtogether.room.RoomRepository
import com.example.watchtogether.signaling.SignalingClient
import com.example.watchtogether.signaling.SignalingListener
import com.example.watchtogether.signaling.SignalingMessage
import com.example.watchtogether.sync.PlaybackSyncManager
import com.example.watchtogether.transfer.FileTransferListener
import com.example.watchtogether.transfer.FileTransferManager
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
    val fileTransferManager = FileTransferManager(application, signalingClient)

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    val syncState: StateFlow<SyncUiState> = syncManager.syncState

    init {
        signalingClient.addListener(this)
        webRtcManager.setListener(this)

        fileTransferManager.setListener(object : FileTransferListener {
            override fun onTransferProgress(progress: Float, statusText: String) {
                _uiState.update {
                    it.copy(
                        isTransferring = progress < 1f,
                        transferProgress = progress,
                        transferStatusText = statusText
                    )
                }
            }

            override fun onFileReceived(localUri: android.net.Uri, fileName: String, mimeType: String) {
                _uiState.update {
                    it.copy(
                        mediaTitle = fileName,
                        mediaUri = localUri.toString(),
                        isTransferring = false,
                        transferProgress = 1f,
                        transferStatusText = "Video ready",
                        videoState = VideoReadinessState.VIDEO_LOADING
                    )
                }
                syncManager.setVideoLoading()
                Log.d("VIEWER", "Setting video source: $localUri")
                videoPlayer.setMedia(localUri, fileName, 0L)
                Log.d("VIEWER", "video.load()")
            }

            override fun onError(error: String) {
                Log.e("TRANSFER ERROR", error)
                _uiState.update {
                    it.copy(
                        isTransferring = false,
                        errorMessage = error
                    )
                }
            }
        })

        viewModelScope.launch {
            signalingClient.connectionState.collect { connState ->
                _uiState.update { it.copy(connectionState = connState) }
            }
        }

        var lastIsPlaying = false
        var lastPositionMs = 0L
        viewModelScope.launch {
            videoPlayer.state.collect { playerState ->
                val state = _uiState.value
                val isReady = !playerState.isLoading && playerState.durationMs > 0
                if (isReady && !syncManager.isVideoReady()) {
                    Log.d("VIEWER", "loadedmetadata")
                    Log.d("VIEWER", "VIDEO_READY")
                    syncManager.onVideoReady()
                    _uiState.update { it.copy(videoState = VideoReadinessState.VIDEO_READY) }
                }

                if (state.roomState == RoomState.ACTIVE && isReady) {
                    if (!syncManager.isRemoteUpdate && !syncManager.isInitializingVideo) {
                        if (playerState.isPlaying != lastIsPlaying) {
                            lastIsPlaying = playerState.isPlaying
                            if (playerState.isPlaying) {
                                syncManager.onLocalPlay()
                            } else {
                                syncManager.onLocalPause()
                            }
                        }

                        if (kotlin.math.abs(playerState.positionMs - lastPositionMs) > 1500L) {
                            syncManager.onLocalSeek(playerState.positionMs)
                        }
                    } else {
                        lastIsPlaying = playerState.isPlaying
                    }
                    lastPositionMs = playerState.positionMs
                } else {
                    lastIsPlaying = playerState.isPlaying
                    lastPositionMs = playerState.positionMs
                }
            }
        }
    }

    fun createRoom(onSuccess: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    roomState = RoomState.WAITING,
                    connectionState = ConnectionState.CONNECTING,
                    role = RoomRole.HOST,
                    currentSession = RoomSession(roomCode = "", role = RoomRole.HOST),
                    errorMessage = null
                )
            }

            val result = roomRepository.createRoom()
            result.onSuccess { response ->
                val code = response.roomCode
                val session = RoomSession(
                    roomCode = code,
                    role = RoomRole.HOST,
                    roomId = response.roomId,
                    expiresAt = response.expiresAt
                )
                _uiState.update {
                    it.copy(
                        roomCode = code,
                        role = RoomRole.HOST,
                        currentSession = session,
                        roomState = RoomState.WAITING
                    )
                }
                webRtcManager.setRole(RoomRole.HOST)
                fileTransferManager.start(code, RoomRole.HOST)
                signalingClient.connect(code, RoomRole.HOST)
                syncManager.start(code, RoomRole.HOST)
                onSuccess?.invoke(code)
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

    fun joinRoom(code: String, onSuccess: ((String) -> Unit)? = null) {
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
                    currentSession = RoomSession(roomCode = normalized, role = RoomRole.VIEWER),
                    errorMessage = null
                )
            }

            val result = roomRepository.joinRoom(normalized)
            result.onSuccess { response ->
                val session = RoomSession(
                    roomCode = normalized,
                    role = RoomRole.VIEWER,
                    roomId = response.roomId,
                    expiresAt = response.expiresAt
                )
                _uiState.update {
                    it.copy(
                        roomCode = normalized,
                        role = RoomRole.VIEWER,
                        currentSession = session,
                        roomState = RoomState.WAITING
                    )
                }
                webRtcManager.setRole(RoomRole.VIEWER)
                fileTransferManager.start(normalized, RoomRole.VIEWER)
                signalingClient.connect(normalized, RoomRole.VIEWER)
                syncManager.start(normalized, RoomRole.VIEWER)
                onSuccess?.invoke(normalized)
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

    fun ensureSession(roomCode: String, role: RoomRole) {
        val current = _uiState.value
        if (current.roomCode != roomCode || current.role != role) {
            val session = RoomSession(roomCode = roomCode, role = role)
            _uiState.update {
                it.copy(
                    roomCode = roomCode,
                    role = role,
                    currentSession = session
                )
            }
            webRtcManager.setRole(role)
            fileTransferManager.start(roomCode, role)
            signalingClient.connect(roomCode, role)
            syncManager.start(roomCode, role)
        }
    }

    fun leaveRoom() {
        val code = _uiState.value.roomCode
        fileTransferManager.stop()
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
    fun notifyMediaSelected(title: String, durationMs: Long, uri: String? = null) {
        val roomCode = _uiState.value.roomCode ?: ""
        if (uri != null) {
            val parsedUri = android.net.Uri.parse(uri)
            if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
                fileTransferManager.setHostLocalMedia(parsedUri, title, durationMs)
            }
        }

        val meta = fileTransferManager.getHostMediaMetadata()
        val mimeType = meta["mimeType"] as? String ?: "video/mp4"
        val fileSize = meta["fileSize"] as? Long ?: 0L

        _uiState.update {
            it.copy(
                mediaTitle = title,
                mediaDurationMs = durationMs,
                mediaUri = uri,
                roomState = RoomState.ACTIVE,
                videoState = VideoReadinessState.VIDEO_READY
            )
        }
        val msg = SignalingMessage.createMediaStarted(
            roomCode = roomCode,
            name = title,
            durationMs = durationMs,
            uri = uri,
            mimeType = mimeType,
            fileSize = fileSize
        )
        signalingClient.send(msg)
        syncManager.onHostMediaStarted(title, durationMs, uri)
        webRtcManager.setVideoSource("Local Media: $title")

        // If viewer is already connected, start transferring local file immediately
        if (_uiState.value.participantConnected && uri != null && !uri.startsWith("http://") && !uri.startsWith("https://")) {
            fileTransferManager.startHostFileTransfer(roomCode)
        }
    }

    // Bidirectional User Playback Actions (Host and Viewer)
    fun onUserPlay() {
        Log.d("[SYNC]", "Local PLAY")
        try {
            videoPlayer.play()
            Log.d("[VIDEO]", "play() succeeded")
        } catch (e: Exception) {
            Log.e("[VIDEO]", "play() rejected", e)
        }
        syncManager.onLocalPlay()
    }

    fun onUserPause() {
        Log.d("[SYNC]", "Local PAUSE")
        videoPlayer.pause()
        syncManager.onLocalPause()
    }

    fun onUserTogglePlayPause() {
        if (videoPlayer.state.value.isPlaying) {
            onUserPause()
        } else {
            onUserPlay()
        }
    }

    fun onUserSeek(targetPositionMs: Long) {
        videoPlayer.seekTo(targetPositionMs)
        syncManager.onLocalSeek(targetPositionMs)
    }

    fun onUserRewind10s() {
        val current = videoPlayer.state.value.positionMs
        val target = (current - 10000L).coerceAtLeast(0L)
        onUserSeek(target)
    }

    fun onUserForward10s() {
        val current = videoPlayer.state.value.positionMs
        val duration = videoPlayer.state.value.durationMs
        val target = if (duration > 0) (current + 10000L).coerceAtMost(duration) else current + 10000L
        onUserSeek(target)
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

        // Reconnection / connection sync request
        if (role == RoomRole.VIEWER) {
            if (syncManager.isVideoReady()) {
                syncManager.requestSync()
            } else {
                Log.d(TAG, "[VIEWER] Requesting current media state on connect/reconnect")
                signalingClient.send(SignalingMessage.createRequestMedia(roomCode))
            }
        }
    }

    override fun onDisconnected(reason: String) {
        Log.d(TAG, "Signaling disconnected: $reason")
    }

    override fun onMessageReceived(message: SignalingMessage) {
        val roomCode = _uiState.value.roomCode ?: return

        // Pass all signaling messages to FileTransferManager
        fileTransferManager.handleSignalingMessage(message)

        when (message.type) {
            SignalingMessage.TYPE_REQUEST_MEDIA -> {
                if (_uiState.value.role == RoomRole.HOST) {
                    val mediaUri = _uiState.value.mediaUri
                    val title = _uiState.value.mediaTitle
                    if (!mediaUri.isNullOrEmpty() && !title.isNullOrEmpty()) {
                        Log.d(TAG, "[HOST] Responding to REQUEST_MEDIA with $title")
                        val meta = fileTransferManager.getHostMediaMetadata()
                        val mimeType = meta["mimeType"] as? String ?: "video/mp4"
                        val fileSize = meta["fileSize"] as? Long ?: 0L
                        val duration = _uiState.value.mediaDurationMs
                        val msg = SignalingMessage.createMediaStarted(
                            roomCode = roomCode,
                            name = title,
                            durationMs = duration,
                            uri = mediaUri,
                            mimeType = mimeType,
                            fileSize = fileSize
                        )
                        signalingClient.send(msg)
                        if (!mediaUri.startsWith("http://") && !mediaUri.startsWith("https://")) {
                            fileTransferManager.startHostFileTransfer(roomCode)
                        }
                    }
                }
            }

            SignalingMessage.TYPE_ROOM_JOINED -> {
                val participants = message.payload.optInt("participantCount", 1)
                _uiState.update {
                    it.copy(
                        participantConnected = participants >= 2,
                        roomState = if (participants >= 2) RoomState.CONNECTED else RoomState.WAITING
                    )
                }

                val mediaInfo = message.payload.optJSONObject("mediaInfo")
                if (mediaInfo != null) {
                    val name = mediaInfo.optString("name")
                    val duration = mediaInfo.optLong("durationMs", 0L)
                    val uri = mediaInfo.optString("uri", "")
                    if (name.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                mediaTitle = name,
                                mediaDurationMs = duration,
                                mediaUri = uri.ifEmpty { null },
                                roomState = RoomState.ACTIVE
                            )
                        }
                        if (_uiState.value.role == RoomRole.VIEWER) {
                            syncManager.setVideoLoading()
                            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                                Log.d("VIEWER", "Setting video source: $uri")
                                videoPlayer.setMedia(android.net.Uri.parse(uri), name, 0L)
                                Log.d("VIEWER", "video.load()")
                            } else {
                                Log.d("TRANSFER", "Requesting video file transfer from Host for $name")
                                signalingClient.send(SignalingMessage.createRequestFile(roomCode))
                            }
                        }
                    }
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
                    val mediaUri = _uiState.value.mediaUri
                    val title = _uiState.value.mediaTitle
                    if (!mediaUri.isNullOrEmpty() && !title.isNullOrEmpty()) {
                        val meta = fileTransferManager.getHostMediaMetadata()
                        val mimeType = meta["mimeType"] as? String ?: "video/mp4"
                        val fileSize = meta["fileSize"] as? Long ?: 0L
                        val duration = _uiState.value.mediaDurationMs
                        val msg = SignalingMessage.createMediaStarted(
                            roomCode = roomCode,
                            name = title,
                            durationMs = duration,
                            uri = mediaUri,
                            mimeType = mimeType,
                            fileSize = fileSize
                        )
                        signalingClient.send(msg)
                        if (!mediaUri.startsWith("http://") && !mediaUri.startsWith("https://")) {
                            fileTransferManager.startHostFileTransfer(roomCode)
                        }
                    }
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
                if (_uiState.value.role == RoomRole.VIEWER) {
                    val sdp = message.payload.optString("sdp")
                    webRtcManager.setRemoteDescription("offer", sdp)
                    webRtcManager.createAnswer(roomCode)
                } else {
                    Log.d(TAG, "Host ignored incoming WEBRTC_OFFER")
                }
            }

            SignalingMessage.TYPE_WEBRTC_ANSWER -> {
                if (_uiState.value.role == RoomRole.HOST) {
                    val sdp = message.payload.optString("sdp")
                    webRtcManager.setRemoteDescription("answer", sdp)
                } else {
                    Log.d(TAG, "Viewer ignored incoming WEBRTC_ANSWER")
                }
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
            SignalingMessage.TYPE_REQUEST_SYNC,
            SignalingMessage.TYPE_MEDIA_STARTED -> {
                syncManager.handleIncomingSignaling(message)
                if (message.type == SignalingMessage.TYPE_MEDIA_STARTED) {
                    val name = message.payload.optString("name")
                    val duration = message.payload.optLong("durationMs", 0L)
                    val uri = message.payload.optString("uri", "")
                    _uiState.update {
                        it.copy(
                            mediaTitle = name,
                            mediaDurationMs = duration,
                            mediaUri = uri.ifEmpty { null },
                            roomState = RoomState.ACTIVE
                        )
                    }
                    if (_uiState.value.role == RoomRole.VIEWER) {
                        syncManager.setVideoLoading()
                        if (uri.startsWith("http://") || uri.startsWith("https://")) {
                            Log.d("VIEWER", "Setting video source: $uri")
                            videoPlayer.setMedia(android.net.Uri.parse(uri), name, 0L)
                            Log.d("VIEWER", "video.load()")
                        } else {
                            Log.d("TRANSFER", "Requesting video file transfer from Host for $name")
                            signalingClient.send(SignalingMessage.createRequestFile(roomCode))
                        }
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
