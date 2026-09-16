package com.example.watchtogether.model

enum class RoomRole {
    HOST,
    VIEWER
}

enum class RoomState {
    IDLE,
    WAITING,
    CONNECTED,
    ACTIVE,
    DISCONNECTED,
    EXPIRED
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

data class RoomSession(
    val roomCode: String,
    val role: RoomRole,
    val roomId: String = "",
    val expiresAt: String = ""
)

data class CreateRoomResponse(
    val roomId: String,
    val roomCode: String,
    val role: RoomRole,
    val expiresAt: String
)

data class JoinRoomResponse(
    val roomId: String,
    val roomCode: String,
    val role: RoomRole,
    val expiresAt: String
)

enum class VideoReadinessState {
    NO_SOURCE,
    VIDEO_LOADING,
    VIDEO_READY,
    SYNCING,
    PLAYING,
    PAUSED
}

data class RoomUiState(
    val roomCode: String? = null,
    val role: RoomRole? = null,
    val currentSession: RoomSession? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val roomState: RoomState = RoomState.IDLE,
    val participantConnected: Boolean = false,
    val mediaTitle: String? = null,
    val mediaDurationMs: Long = 0L,
    val mediaUri: String? = null,
    val videoState: VideoReadinessState = VideoReadinessState.NO_SOURCE,
    val errorMessage: String? = null,
    val isTransferring: Boolean = false,
    val transferProgress: Float = 0f,
    val transferStatusText: String = "",
    val isHost: Boolean = (role ?: currentSession?.role) == RoomRole.HOST
)

data class SyncUiState(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val driftMs: Long = 0L,
    val isInSync: Boolean = true,
    val lastSequence: Long = 0L
)
