package com.example.domain.model

import android.net.Uri

data class VideoItem(
    val id: Long = 0L,
    val uri: String,
    val displayName: String,
    val mimeType: String = "video/*",
    val size: Long = 0L,
    val durationMs: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val folderName: String = "Unknown",
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val lastPlayedAt: Long = 0L,
    val lastPositionMs: Long = 0L,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val completed: Boolean = false
) {
    val parsedUri: Uri
        get() = Uri.parse(uri)

    val progress: Float
        get() = if (durationMs > 0) (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val resolutionFormatted: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else "Unknown"
}

data class AudioTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val channels: Int = 0,
    val sampleRate: Int = 0,
    val mimeType: String? = null
)

data class SubtitleTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val uri: String? = null,
    val isExternal: Boolean = false,
    val mimeType: String? = null
)

enum class ResizeMode {
    FIT,
    FILL,
    ZOOM,
    ORIGINAL,
    RATIO_16_9,
    RATIO_4_3
}

sealed class PlayerError(val message: String, val technicalDetails: String? = null) {
    class UnsupportedCodec(details: String?) : PlayerError("Unsupported video or audio format on this device.", details)
    class CorruptedFile(details: String?) : PlayerError("This video file appears corrupted or unreadable.", details)
    class MissingFile(details: String?) : PlayerError("The video file could not be found or storage permission was revoked.", details)
    class DecoderError(details: String?) : PlayerError("Hardware decoder encountered a failure.", details)
    class Unknown(details: String?) : PlayerError("An unexpected playback error occurred.", details)
}

data class PlayerUiState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val isFullscreen: Boolean = false,
    val isLocked: Boolean = false,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedAudioTrack: AudioTrack? = null,
    val selectedSubtitleTrack: SubtitleTrack? = null,
    val audioDelayMs: Long = 0L,
    val subtitleDelayMs: Long = 0L,
    val error: PlayerError? = null,
    val videoTitle: String = "",
    val videoUri: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val bufferedProgress: Float
        get() = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

sealed class PlayerEvent {
    data class ShowToast(val message: String) : PlayerEvent()
    data class ShowResumeDialog(val videoTitle: String, val resumePositionMs: Long, val durationMs: Long) : PlayerEvent()
    data class ErrorOccurred(val error: PlayerError) : PlayerEvent()
    object PlaybackCompleted : PlayerEvent()
}
