package com.example.presentation.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.VideoItem
import com.example.domain.repository.VideoRepository
import com.example.domain.usecase.GetLibraryUseCase
import com.example.domain.usecase.GetPlaybackResumeUseCase
import com.example.domain.usecase.LibraryState
import com.example.domain.usecase.ResumeDecision
import com.example.domain.usecase.ScanVideosUseCase
import com.example.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VideoSortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_ADDED_DESC,
    SIZE_DESC,
    DURATION_DESC
}

class HomeViewModel(
    private val videoRepository: VideoRepository,
    private val getLibraryUseCase: GetLibraryUseCase,
    private val scanVideosUseCase: ScanVideosUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val getPlaybackResumeUseCase: GetPlaybackResumeUseCase
) : ViewModel() {

    val libraryState: StateFlow<LibraryState> = getLibraryUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryState(isLoading = true)
        )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(VideoSortOrder.NAME_ASC)
    val sortOrder: StateFlow<VideoSortOrder> = _sortOrder.asStateFlow()

    val searchResults: StateFlow<List<VideoItem>> = _searchQuery
        .debounce(250L)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                videoRepository.getAllVideos()
            } else {
                videoRepository.searchVideos(query.trim())
            }
        }
        .combine(_sortOrder) { list, sort ->
            sortVideos(list, sort)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        rescanLibrary()
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanVideosUseCase.scanMediaStore()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun addSafFolder(folderUri: Uri, folderName: String) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanVideosUseCase.scanSafFolder(folderUri, folderName)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setSortOrder(order: VideoSortOrder) {
        _sortOrder.value = order
    }

    fun toggleFavorite(uri: String) {
        viewModelScope.launch {
            toggleFavoriteUseCase(uri)
        }
    }

    fun deleteVideo(uri: String) {
        viewModelScope.launch {
            videoRepository.deleteVideoEntry(uri)
        }
    }

    suspend fun checkResumeDecision(videoUri: String): ResumeDecision {
        return getPlaybackResumeUseCase(videoUri)
    }

    private fun sortVideos(list: List<VideoItem>, sortOrder: VideoSortOrder): List<VideoItem> {
        return when (sortOrder) {
            VideoSortOrder.NAME_ASC -> list.sortedBy { it.displayName.lowercase() }
            VideoSortOrder.NAME_DESC -> list.sortedByDescending { it.displayName.lowercase() }
            VideoSortOrder.DATE_ADDED_DESC -> list.sortedByDescending { it.dateAdded }
            VideoSortOrder.SIZE_DESC -> list.sortedByDescending { it.size }
            VideoSortOrder.DURATION_DESC -> list.sortedByDescending { it.durationMs }
        }
    }
}
