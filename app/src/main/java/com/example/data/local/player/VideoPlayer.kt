package com.example.data.local.player

import android.net.Uri
import androidx.media3.ui.PlayerView
import com.example.domain.model.AudioTrack
import com.example.domain.model.PlayerError
import com.example.domain.model.PlayerEvent
import com.example.domain.model.PlayerUiState
import com.example.domain.model.ResizeMode
import com.example.domain.model.SubtitleTrack
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface VideoPlayer {
    val state: StateFlow<PlayerUiState>
    val events: SharedFlow<PlayerEvent>

    fun setMedia(uri: Uri, title: String? = null, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekBy(offsetMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun setMuted(muted: Boolean)
    fun selectAudioTrack(track: AudioTrack)
    fun selectSubtitleTrack(track: SubtitleTrack?)
    fun addExternalSubtitle(uri: Uri, displayName: String)
    fun setAudioDelay(delayMs: Long)
    fun setSubtitleDelay(delayMs: Long)
    fun setResizeMode(mode: ResizeMode)
    fun setControlsLocked(locked: Boolean)
    fun attachSurfaceView(playerView: PlayerView)
    fun release()
}
