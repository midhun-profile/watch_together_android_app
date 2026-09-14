package com.example.domain.player

import android.net.Uri

enum class TrackType {
    AUDIO,
    SUBTITLE
}

data class TrackInfo(
    val id: Int,
    val name: String,
    val language: String? = null,
    val selected: Boolean = false,
    val type: TrackType
)

enum class AspectRatioMode(val label: String, val vlcRatio: String?) {
    FIT("Fit", null),
    FILL("Fill", "fill"),
    ORIGINAL("Original", "original"),
    RATIO_16_9("16:9", "16:9"),
    RATIO_4_3("4:3", "4:3")
}

enum class HardwareAcceleration(val label: String) {
    AUTOMATIC("Automatic"),
    ENABLED("Enabled"),
    DISABLED("Disabled")
}

data class FileDetails(
    val fileName: String,
    val uriString: String,
    val container: String,
    val durationFormatted: String,
    val fileSizeFormatted: String,
    val resolution: String? = null,
    val frameRate: String? = null,
    val videoCodec: String? = null,
    val videoBitrate: String? = null,
    val audioCodec: String? = null,
    val audioChannels: String? = null,
    val audioTrackCount: Int = 0,
    val subtitleTrackCount: Int = 0
)

data class MediaItem(
    val uri: Uri,
    val title: String,
    val fileName: String,
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val startPositionMs: Long = 0L
)
