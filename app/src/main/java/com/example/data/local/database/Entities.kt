package com.example.data.local.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.VideoItem

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["dateAdded"]),
        Index(value = ["folderName"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["isFavorite"])
    ]
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val uri: String,
    val displayName: String,
    val mimeType: String = "video/*",
    val size: Long = 0L,
    val duration: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val folderName: String = "Unknown",
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val thumbnailReference: String? = null,
    val lastPlayedAt: Long = 0L,
    val lastPosition: Long = 0L,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val completed: Boolean = false
) {
    fun toDomainModel(): VideoItem {
        return VideoItem(
            id = id,
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            size = size,
            durationMs = duration,
            dateAdded = dateAdded,
            dateModified = dateModified,
            folderName = folderName,
            width = width,
            height = height,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            lastPlayedAt = lastPlayedAt,
            lastPositionMs = lastPosition,
            playCount = playCount,
            isFavorite = isFavorite,
            completed = completed
        )
    }
}

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["lastPlayedAt"])
    ]
)
data class PlaybackEntity(
    @PrimaryKey
    val videoUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subtitles",
    indices = [
        Index(value = ["videoUri"])
    ]
)
data class SubtitleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val videoUri: String,
    val uri: String,
    val language: String? = null,
    val displayName: String,
    val subtitleType: String = "SRT",
    val isExternal: Boolean = true
)

@Entity(tableName = "saf_folders")
data class SafFolderEntity(
    @PrimaryKey
    val uri: String,
    val displayName: String,
    val dateAdded: Long = System.currentTimeMillis()
)
