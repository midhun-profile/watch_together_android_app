package com.example.domain.usecase

import com.example.domain.model.VideoItem
import com.example.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LibraryState(
    val continueWatching: List<VideoItem> = emptyList(),
    val recentlyAdded: List<VideoItem> = emptyList(),
    val favorites: List<VideoItem> = emptyList(),
    val allVideos: List<VideoItem> = emptyList(),
    val folders: List<String> = emptyList(),
    val isLoading: Boolean = false
)

class GetLibraryUseCase(private val repository: VideoRepository) {
    operator fun invoke(): Flow<LibraryState> {
        return combine(
            repository.getContinueWatchingVideos(),
            repository.getRecentlyAddedVideos(),
            repository.getFavoriteVideos(),
            repository.getAllVideos(),
            repository.getFolders()
        ) { continueWatching, recentlyAdded, favorites, allVideos, folders ->
            LibraryState(
                continueWatching = continueWatching,
                recentlyAdded = recentlyAdded,
                favorites = favorites,
                allVideos = allVideos,
                folders = folders,
                isLoading = false
            )
        }
    }
}
