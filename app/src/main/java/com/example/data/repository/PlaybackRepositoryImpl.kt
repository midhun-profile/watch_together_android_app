package com.example.data.repository

import com.example.data.local.database.PlaybackDao
import com.example.data.local.database.PlaybackEntity
import com.example.data.local.database.VideoDao
import com.example.domain.repository.PlaybackRecord
import com.example.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaybackRepositoryImpl(
    private val playbackDao: PlaybackDao,
    private val videoDao: VideoDao
) : PlaybackRepository {

    override fun getPlayback(videoUri: String): Flow<PlaybackRecord?> {
        return playbackDao.getPlayback(videoUri).map { entity ->
            entity?.let {
                PlaybackRecord(
                    videoUri = it.videoUri,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                    completed = it.completed,
                    lastPlayedAt = it.lastPlayedAt
                )
            }
        }
    }

    override suspend fun getPlaybackSync(videoUri: String): PlaybackRecord? {
        val entity = playbackDao.getPlaybackSync(videoUri)
        return entity?.let {
            PlaybackRecord(
                videoUri = it.videoUri,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                completed = it.completed,
                lastPlayedAt = it.lastPlayedAt
            )
        }
    }

    override suspend fun savePlaybackPosition(
        videoUri: String,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean
    ) {
        val now = System.currentTimeMillis()
        playbackDao.savePlayback(
            PlaybackEntity(
                videoUri = videoUri,
                positionMs = positionMs,
                durationMs = durationMs,
                completed = completed,
                lastPlayedAt = now
            )
        )
        videoDao.updatePlaybackPosition(
            uri = videoUri,
            position = positionMs,
            duration = durationMs,
            completed = completed,
            timestamp = now
        )
    }

    override suspend fun clearHistory() {
        playbackDao.clearAllHistory()
    }
}
