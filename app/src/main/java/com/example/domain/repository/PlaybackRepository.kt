package com.example.domain.repository

import kotlinx.coroutines.flow.Flow

interface PlaybackRepository {
    fun getPlayback(videoUri: String): Flow<PlaybackRecord?>
    suspend fun getPlaybackSync(videoUri: String): PlaybackRecord?
    suspend fun savePlaybackPosition(videoUri: String, positionMs: Long, durationMs: Long, completed: Boolean)
    suspend fun clearHistory()
}

data class PlaybackRecord(
    val videoUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastPlayedAt: Long
)
