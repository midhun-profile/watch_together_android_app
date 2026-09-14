package com.example.presentation.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.PlayerView
import com.example.data.local.player.VideoPlayer
import com.example.data.local.preferences.PlayerPreferences
import com.example.domain.model.AudioTrack
import com.example.domain.model.PlayerEvent
import com.example.domain.model.PlayerUiState
import com.example.domain.model.ResizeMode
import com.example.domain.model.SubtitleTrack
import com.example.domain.usecase.SavePlaybackPositionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val videoPlayer: VideoPlayer,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
    private val playerPreferences: PlayerPreferences
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = videoPlayer.state
    val events: SharedFlow<PlayerEvent> = videoPlayer.events

    private var autoSaveJob: Job? = null
    private var currentUri: String? = null

    init {
        viewModelScope.launch {
            val settings = playerPreferences.settingsFlow.first()
            videoPlayer.setPlaybackSpeed(settings.defaultPlaybackSpeed)
            videoPlayer.setResizeMode(settings.defaultResizeMode)
        }

        // Periodic debounced position auto-save
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(3000L)
                persistCurrentPosition()
            }
        }
    }

    fun attachPlayerView(playerView: PlayerView) {
        videoPlayer.attachSurfaceView(playerView)
    }

    fun loadVideo(uri: Uri, title: String? = null, startPositionMs: Long = 0L) {
        currentUri = uri.toString()
        videoPlayer.setMedia(uri, title, startPositionMs)
    }

    fun play() = videoPlayer.play()
    fun pause() {
        videoPlayer.pause()
        persistCurrentPosition()
    }
    fun togglePlayPause() {
        videoPlayer.togglePlayPause()
        persistCurrentPosition()
    }

    fun seekTo(positionMs: Long) {
        videoPlayer.seekTo(positionMs)
        persistCurrentPosition()
    }

    fun seekBy(offsetMs: Long) {
        videoPlayer.seekBy(offsetMs)
        persistCurrentPosition()
    }

    fun setPlaybackSpeed(speed: Float) {
        videoPlayer.setPlaybackSpeed(speed)
    }

    fun setVolume(volume: Float) {
        videoPlayer.setVolume(volume)
    }

    fun selectAudioTrack(track: AudioTrack) {
        videoPlayer.selectAudioTrack(track)
    }

    fun selectSubtitleTrack(track: SubtitleTrack?) {
        videoPlayer.selectSubtitleTrack(track)
    }

    fun addExternalSubtitle(uri: Uri, displayName: String) {
        videoPlayer.addExternalSubtitle(uri, displayName)
    }

    fun setAudioDelay(delayMs: Long) {
        videoPlayer.setAudioDelay(delayMs)
    }

    fun setSubtitleDelay(delayMs: Long) {
        videoPlayer.setSubtitleDelay(delayMs)
    }

    fun setResizeMode(mode: ResizeMode) {
        videoPlayer.setResizeMode(mode)
    }

    fun setControlsLocked(locked: Boolean) {
        videoPlayer.setControlsLocked(locked)
    }

    private fun persistCurrentPosition() {
        val uri = currentUri ?: return
        val s = state.value
        if (s.durationMs > 0 && s.positionMs > 0) {
            viewModelScope.launch {
                savePlaybackPositionUseCase(
                    videoUri = uri,
                    positionMs = s.positionMs,
                    durationMs = s.durationMs
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        persistCurrentPosition()
        videoPlayer.release()
    }
}
