package com.example.player

import android.content.Context
import android.net.Uri
import com.example.data.media.MediaRepository
import com.example.domain.player.AspectRatioMode
import com.example.domain.player.HardwareAcceleration
import com.example.domain.player.MediaItem
import com.example.domain.player.TrackInfo
import com.example.utils.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout

interface PlayerController {
    val state: StateFlow<PlayerState>
    val events: SharedFlow<PlayerEvent>

    fun attachLayout(layout: VLCVideoLayout)
    fun detachLayout()
    fun openMedia(uri: Uri, title: String? = null, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekForward(deltaMs: Long = 10_000L)
    fun seekBackward(deltaMs: Long = 10_000L)
    fun setVolume(volume: Int)
    fun toggleMute()
    fun setPlaybackSpeed(speed: Float)
    fun setSubtitleTrack(trackId: Int)
    fun setAudioTrack(trackId: Int)
    fun setSubtitleDelay(delayMs: Long)
    fun setAudioDelay(delayMs: Long)
    fun setSubtitleSize(sizeSp: Int)
    fun toggleSubtitles()
    fun setAspectRatio(mode: AspectRatioMode)
    fun setHardwareAcceleration(hw: HardwareAcceleration)
    fun addExternalSubtitle(uri: Uri)
    fun toggleFullscreen()
    fun setFullscreen(fullscreen: Boolean)
    fun clearError()
    fun stop()
    fun release()

    // Future-Proof Sync hooks (Task 2 synchronization ready)
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun isPlaying(): Boolean
}

class PlayerControllerImpl(
    private val appContext: Context,
    private val mediaRepository: MediaRepository
) : PlayerController, MediaEngine.EngineListener {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mediaEngine = MediaEngine(appContext)

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var tickerJob: Job? = null
    private var lastSavedPosition: Long = 0L
    private var unmutedVolume: Int = 100

    init {
        mediaEngine.setListener(this)
        startPositionTicker()
    }

    override fun attachLayout(layout: VLCVideoLayout) {
        mediaEngine.attachLayout(layout)
    }

    override fun detachLayout() {
        mediaEngine.detachLayout()
    }

    override fun openMedia(uri: Uri, title: String?, startPositionMs: Long) {
        val fileName = Formatters.getFileName(appContext, uri)
        val fileSize = Formatters.getFileSize(appContext, uri)
        val resolvedTitle = title ?: fileName

        val mediaItem = MediaItem(
            uri = uri,
            title = resolvedTitle,
            fileName = fileName,
            fileSize = fileSize,
            startPositionMs = startPositionMs
        )

        _state.update {
            it.copy(
                currentMedia = mediaItem,
                currentPositionMs = startPositionMs,
                durationMs = 0L,
                isPlaying = false,
                isBuffering = true,
                isCompleted = false,
                errorMessage = null
            )
        }

        mediaEngine.loadMedia(uri, startPositionMs)

        // Query repository for saved preferences
        scope.launch {
            val saved = mediaRepository.getMovie(uri.toString())
            if (saved != null) {
                if (saved.playbackSpeed > 0f) {
                    setPlaybackSpeed(saved.playbackSpeed)
                }
                if (saved.subtitleDelayMs != 0L) {
                    setSubtitleDelay(saved.subtitleDelayMs)
                }
                if (saved.audioDelayMs != 0L) {
                    setAudioDelay(saved.audioDelayMs)
                }
            }
        }
    }

    override fun play() {
        _state.update { it.copy(isPlaying = true, isCompleted = false) }
        mediaEngine.play()
    }

    override fun pause() {
        _state.update { it.copy(isPlaying = false) }
        mediaEngine.pause()
        saveCurrentProgress()
    }

    override fun togglePlayPause() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    override fun seekTo(positionMs: Long) {
        val duration = _state.value.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        _state.update { it.copy(currentPositionMs = target, isCompleted = false) }
        mediaEngine.seekTo(target)
        saveCurrentProgress()
    }

    override fun seekForward(deltaMs: Long) {
        val current = _state.value.currentPositionMs
        val target = current + deltaMs
        seekTo(target)
        _events.tryEmit(PlayerEvent.SeekFeedback((deltaMs / 1000).toInt(), isForward = true))
    }

    override fun seekBackward(deltaMs: Long) {
        val current = _state.value.currentPositionMs
        val target = (current - deltaMs).coerceAtLeast(0L)
        seekTo(target)
        _events.tryEmit(PlayerEvent.SeekFeedback((deltaMs / 1000).toInt(), isForward = false))
    }

    override fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        mediaEngine.setVolume(clamped)
        if (clamped > 0) {
            unmutedVolume = clamped
        }
        _state.update {
            it.copy(
                volume = clamped,
                isMuted = clamped == 0
            )
        }
    }

    override fun toggleMute() {
        if (_state.value.isMuted) {
            setVolume(if (unmutedVolume > 0) unmutedVolume else 100)
        } else {
            unmutedVolume = _state.value.volume
            setVolume(0)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        mediaEngine.setPlaybackRate(speed)
        _state.update { it.copy(playbackSpeed = speed) }
        saveCurrentProgress()
    }

    override fun setSubtitleTrack(trackId: Int) {
        mediaEngine.setSubtitleTrack(trackId)
        _state.update {
            it.copy(
                selectedSubtitleTrackId = trackId,
                subtitlesEnabled = trackId != -1
            )
        }
        updateTrackLists()
        saveCurrentProgress()
    }

    override fun setAudioTrack(trackId: Int) {
        mediaEngine.setAudioTrack(trackId)
        _state.update { it.copy(selectedAudioTrackId = trackId) }
        updateTrackLists()
        saveCurrentProgress()
    }

    override fun setSubtitleDelay(delayMs: Long) {
        mediaEngine.setSubtitleDelay(delayMs)
        _state.update { it.copy(subtitleDelayMs = delayMs) }
        saveCurrentProgress()
    }

    override fun setAudioDelay(delayMs: Long) {
        mediaEngine.setAudioDelay(delayMs)
        _state.update { it.copy(audioDelayMs = delayMs) }
        saveCurrentProgress()
    }

    override fun setSubtitleSize(sizeSp: Int) {
        _state.update { it.copy(subtitleSizeSp = sizeSp.coerceIn(12, 36)) }
    }

    override fun toggleSubtitles() {
        val currentEnabled = _state.value.subtitlesEnabled
        if (currentEnabled) {
            setSubtitleTrack(-1)
        } else {
            val tracks = _state.value.availableSubtitleTracks
            val firstRealTrack = tracks.firstOrNull { it.id != -1 }
            if (firstRealTrack != null) {
                setSubtitleTrack(firstRealTrack.id)
            }
        }
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        mediaEngine.setAspectRatio(mode)
        _state.update { it.copy(aspectRatio = mode) }
    }

    override fun setHardwareAcceleration(hw: HardwareAcceleration) {
        mediaEngine.setHardwareAcceleration(hw)
        _state.update { it.copy(hardwareAcceleration = hw) }
    }

    override fun addExternalSubtitle(uri: Uri) {
        mediaEngine.addExternalSubtitle(uri)
        _events.tryEmit(PlayerEvent.ShowToast("External subtitle attached"))
        scope.launch {
            delay(400)
            updateTrackLists()
        }
    }

    override fun toggleFullscreen() {
        _state.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    override fun setFullscreen(fullscreen: Boolean) {
        _state.update { it.copy(isFullscreen = fullscreen) }
    }

    override fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun stop() {
        saveCurrentProgress()
        mediaEngine.stop()
        _state.update {
            it.copy(
                isPlaying = false,
                isBuffering = false
            )
        }
    }

    override fun release() {
        saveCurrentProgress()
        tickerJob?.cancel()
        mediaEngine.release()
    }

    override fun getCurrentPosition(): Long = mediaEngine.getCurrentPosition()
    override fun getDuration(): Long = mediaEngine.getDuration()
    override fun isPlaying(): Boolean = mediaEngine.isPlaying()

    // EngineListener implementations
    override fun onPlaying() {
        _state.update {
            it.copy(
                isPlaying = true,
                isBuffering = false,
                errorMessage = null
            )
        }
        updateTrackLists()
    }

    override fun onPaused() {
        _state.update { it.copy(isPlaying = false) }
    }

    override fun onStopped() {
        _state.update { it.copy(isPlaying = false) }
    }

    override fun onBuffering(bufferPercent: Float) {
        _state.update {
            it.copy(
                isBuffering = bufferPercent < 100f
            )
        }
    }

    override fun onTimeChanged(currentTimeMs: Long) {
        // Updated periodically by ticker to throttle recompositions,
        // but ensure state tracks non-zero
        if (_state.value.currentPositionMs == 0L && currentTimeMs > 0L) {
            _state.update { it.copy(currentPositionMs = currentTimeMs) }
        }
    }

    override fun onLengthChanged(lengthMs: Long) {
        _state.update { it.copy(durationMs = lengthMs) }
        updateTrackLists()
    }

    override fun onTracksChanged() {
        updateTrackLists()
    }

    override fun onEndReached() {
        _state.update {
            it.copy(
                isPlaying = false,
                isCompleted = true,
                currentPositionMs = it.durationMs
            )
        }
        _events.tryEmit(PlayerEvent.PlaybackEnded)
        saveCurrentProgress()
    }

    override fun onError(message: String) {
        _state.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = message
            )
        }
        _events.tryEmit(PlayerEvent.Error(message))
    }

    private fun updateTrackLists() {
        val audioTracks = mediaEngine.getAudioTracks()
        val subtitleTracks = mediaEngine.getSubtitleTracks()

        val selectedAudio = audioTracks.firstOrNull { it.selected }?.id ?: -1
        val selectedSub = subtitleTracks.firstOrNull { it.selected }?.id ?: -1

        _state.update {
            it.copy(
                availableAudioTracks = audioTracks,
                selectedAudioTrackId = selectedAudio,
                availableSubtitleTracks = subtitleTracks,
                selectedSubtitleTrackId = selectedSub,
                subtitlesEnabled = selectedSub != -1
            )
        }
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(300)
                if (_state.value.isPlaying) {
                    val pos = mediaEngine.getCurrentPosition()
                    val dur = mediaEngine.getDuration()
                    _state.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = if (dur > 0) dur else it.durationMs
                        )
                    }

                    // Periodically persist progress (every 5 seconds)
                    if (kotlin.math.abs(pos - lastSavedPosition) > 5000L) {
                        saveCurrentProgress()
                        lastSavedPosition = pos
                    }
                }
            }
        }
    }

    private fun saveCurrentProgress() {
        val currentMedia = _state.value.currentMedia ?: return
        val pos = _state.value.currentPositionMs
        val dur = _state.value.durationMs
        val speed = _state.value.playbackSpeed
        val audioId = _state.value.selectedAudioTrackId
        val subId = _state.value.selectedSubtitleTrackId
        val subDelay = _state.value.subtitleDelayMs
        val audioDelay = _state.value.audioDelayMs
        val fileSize = currentMedia.fileSize

        scope.launch {
            mediaRepository.savePlaybackProgress(
                uriString = currentMedia.uri.toString(),
                fileName = currentMedia.fileName,
                positionMs = pos,
                durationMs = dur,
                playbackSpeed = speed,
                audioTrackId = audioId,
                subtitleTrackId = subId,
                subtitleDelayMs = subDelay,
                audioDelayMs = audioDelay,
                fileSize = fileSize
            )
        }
    }
}
