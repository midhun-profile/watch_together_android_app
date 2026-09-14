package com.example.domain.usecase

import com.example.domain.repository.PlaybackRepository

class SavePlaybackPositionUseCase(private val repository: PlaybackRepository) {
    suspend operator fun invoke(
        videoUri: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val completed = durationMs > 0 && positionMs >= (durationMs - 10_000L)
        repository.savePlaybackPosition(videoUri, positionMs, durationMs, completed)
    }
}
