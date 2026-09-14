package com.example.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(private val dao: RecentMovieDao) {

    val allRecentMovies: Flow<List<RecentMovieEntity>> = dao.getAllRecentMovies()

    suspend fun getMovie(uriString: String): RecentMovieEntity? = withContext(Dispatchers.IO) {
        dao.getMovie(uriString)
    }

    suspend fun savePlaybackProgress(
        uriString: String,
        fileName: String,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float = 1.0f,
        audioTrackId: Int = -1,
        subtitleTrackId: Int = -1,
        subtitleDelayMs: Long = 0L,
        audioDelayMs: Long = 0L,
        fileSize: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val entity = RecentMovieEntity(
            uriString = uriString,
            fileName = fileName,
            lastPositionMs = positionMs,
            durationMs = durationMs,
            lastPlayedTimestamp = System.currentTimeMillis(),
            playbackSpeed = playbackSpeed,
            audioTrackId = audioTrackId,
            subtitleTrackId = subtitleTrackId,
            subtitleDelayMs = subtitleDelayMs,
            audioDelayMs = audioDelayMs,
            fileSize = fileSize
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun deleteRecent(uriString: String) = withContext(Dispatchers.IO) {
        dao.deleteMovie(uriString)
    }

    suspend fun clearRecents() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
