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

data class RoomUiState(
    val roomCode: String? = null,
    val role: RoomRole? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val roomState: RoomState = RoomState.IDLE,
    val participantConnected: Boolean = false,
    val mediaTitle: String? = null,
    val mediaDurationMs: Long = 0L,
    val errorMessage: String? = null,
    val isHost: Boolean = role == RoomRole.HOST
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
