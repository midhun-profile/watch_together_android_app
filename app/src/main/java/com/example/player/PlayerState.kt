package com.example.player

import com.example.domain.player.AspectRatioMode
import com.example.domain.player.HardwareAcceleration
import com.example.domain.player.MediaItem
import com.example.domain.player.TrackInfo

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volume: Int = 100,
    val isMuted: Boolean = false,
    val currentMedia: MediaItem? = null,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrackId: Int = -1,
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val selectedSubtitleTrackId: Int = -1,
    val subtitleDelayMs: Long = 0L,
    val audioDelayMs: Long = 0L,
    val subtitleSizeSp: Int = 18,
    val subtitlesEnabled: Boolean = true,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.AUTOMATIC,
    val isFullscreen: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
