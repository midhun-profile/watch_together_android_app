package com.example.domain.usecase

import com.example.domain.repository.VideoRepository

class ToggleFavoriteUseCase(private val repository: VideoRepository) {
    suspend operator fun invoke(uri: String) {
        repository.toggleFavorite(uri)
    }
}
