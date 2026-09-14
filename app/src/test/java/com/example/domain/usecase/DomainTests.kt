package com.example.domain.usecase

import com.example.domain.model.ResizeMode
import com.example.domain.model.VideoItem
import com.example.domain.repository.PlaybackRecord
import com.example.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTests {

    @Test
    fun testVideoItemProgress() {
        val video = VideoItem(
            uri = "content://media/1",
            displayName = "Sample Movie.mp4",
            durationMs = 100_000L,
            lastPositionMs = 25_000L
        )
        assertEquals(0.25f, video.progress, 0.001f)
    }

    @Test
    fun testResumeDecisionPrompt() = runBlocking {
        val fakePlaybackRepo = object : PlaybackRepository {
            override fun getPlayback(videoUri: String): Flow<PlaybackRecord?> = flowOf(null)
            override suspend fun getPlaybackSync(videoUri: String): PlaybackRecord {
                return PlaybackRecord(
                    videoUri = videoUri,
                    positionMs = 30_000L,
                    durationMs = 120_000L,
                    completed = false,
                    lastPlayedAt = 1000L
                )
            }
            override suspend fun savePlaybackPosition(videoUri: String, positionMs: Long, durationMs: Long, completed: Boolean) {}
            override suspend fun clearHistory() {}
        }

        val useCase = GetPlaybackResumeUseCase(fakePlaybackRepo)
        val decision = useCase("content://media/1")
        assertTrue(decision.shouldPromptResume)
        assertEquals(30_000L, decision.positionMs)
    }

    @Test
    fun testCompletedDoesNotPromptResume() = runBlocking {
        val fakePlaybackRepo = object : PlaybackRepository {
            override fun getPlayback(videoUri: String): Flow<PlaybackRecord?> = flowOf(null)
            override suspend fun getPlaybackSync(videoUri: String): PlaybackRecord {
                return PlaybackRecord(
                    videoUri = videoUri,
                    positionMs = 118_000L,
                    durationMs = 120_000L,
                    completed = true,
                    lastPlayedAt = 1000L
                )
            }
            override suspend fun savePlaybackPosition(videoUri: String, positionMs: Long, durationMs: Long, completed: Boolean) {}
            override suspend fun clearHistory() {}
        }

        val useCase = GetPlaybackResumeUseCase(fakePlaybackRepo)
        val decision = useCase("content://media/1")
        assertFalse(decision.shouldPromptResume)
    }
}
