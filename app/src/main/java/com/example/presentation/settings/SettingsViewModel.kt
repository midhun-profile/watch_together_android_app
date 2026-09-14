package com.example.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.preferences.PlayerPreferences
import com.example.data.local.preferences.PlayerSettings
import com.example.domain.model.ResizeMode
import com.example.domain.repository.SafFolder
import com.example.domain.repository.VideoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: PlayerPreferences,
    private val videoRepository: VideoRepository
) : ViewModel() {

    val settings: StateFlow<PlayerSettings> = preferences.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerSettings()
        )

    val safFolders: StateFlow<List<SafFolder>> = videoRepository.getSafFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSpeed(speed: Float) {
        viewModelScope.launch { preferences.setSpeed(speed) }
    }

    fun setResumeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setResumeEnabled(enabled) }
    }

    fun setSeekInterval(seconds: Int) {
        viewModelScope.launch { preferences.setSeekInterval(seconds) }
    }

    fun setResizeMode(mode: ResizeMode) {
        viewModelScope.launch { preferences.setResizeMode(mode) }
    }

    fun setGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setGesturesEnabled(enabled) }
    }

    fun setHardwareDecoder(prefer: Boolean) {
        viewModelScope.launch { preferences.setHardwareDecoder(prefer) }
    }

    fun setSubtitleSize(sizeSp: Float) {
        viewModelScope.launch { preferences.setSubtitleSize(sizeSp) }
    }

    fun setThemeMode(theme: String) {
        viewModelScope.launch { preferences.setThemeMode(theme) }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch { videoRepository.removeSafFolder(uri) }
    }
}
