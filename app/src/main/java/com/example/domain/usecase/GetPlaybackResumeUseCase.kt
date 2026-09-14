package com.example.domain.usecase

import com.example.domain.repository.PlaybackRepository
import com.example.domain.repository.PlaybackRecord

data class ResumeDecision(
    val shouldPromptResume: Boolean,
    val positionMs: Long,
    val durationMs: Long
)

class GetPlaybackResumeUseCase(private val repository: PlaybackRepository) {
    suspend operator fun invoke(videoUri: String): ResumeDecision {
        val record: PlaybackRecord? = repository.getPlaybackSync(videoUri)
        if (record != null && !record.completed && record.positionMs > 5000L &&
            (record.durationMs == 0L || record.positionMs < record.durationMs - 10000L)
        ) {
            return ResumeDecision(
                shouldPromptResume = true,
                positionMs = record.positionMs,
                durationMs = record.durationMs
            )
        }
        return ResumeDecision(
            shouldPromptResume = false,
            positionMs = 0L,
            durationMs = record?.durationMs ?: 0L
        )
    }
}
