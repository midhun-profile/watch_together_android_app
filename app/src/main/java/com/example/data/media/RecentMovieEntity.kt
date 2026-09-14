package com.example.data.media

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_movies")
data class RecentMovieEntity(
    @PrimaryKey
    val uriString: String,
    val fileName: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastPlayedTimestamp: Long,
    val playbackSpeed: Float = 1.0f,
    val audioTrackId: Int = -1,
    val subtitleTrackId: Int = -1,
    val subtitleDelayMs: Long = 0L,
    val audioDelayMs: Long = 0L,
    val fileSize: Long = 0L
)
