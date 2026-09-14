package com.example.player

sealed interface PlayerEvent {
    data class SeekFeedback(val deltaSeconds: Int, val isForward: Boolean) : PlayerEvent
    data class ShowToast(val message: String) : PlayerEvent
    data class Error(val message: String) : PlayerEvent
    data object PlaybackEnded : PlayerEvent
}
