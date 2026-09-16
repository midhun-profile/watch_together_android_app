package com.example.data.local.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.domain.model.AudioTrack
import com.example.domain.model.PlayerError
import com.example.domain.model.PlayerEvent
import com.example.domain.model.PlayerUiState
import com.example.domain.model.ResizeMode
import com.example.domain.model.SubtitleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class Media3PlayerManager(
    private val context: Context,
    preferHardwareDecoder: Boolean = true
) : VideoPlayer {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val trackSelector = DefaultTrackSelector(context)
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var attachedPlayerView: PlayerView? = null

    // Track mapping cache
    private var currentTracks: Tracks? = null
    private val externalSubtitles = mutableListOf<MediaItem.SubtitleConfiguration>()
    private var currentMediaUri: Uri? = null
    private var currentMediaTitle: String = ""

    init {
        initPlayer(preferHardwareDecoder)
    }

    private fun initPlayer(preferHardware: Boolean) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            val mode = if (preferHardware) {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            } else {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
            setExtensionRendererMode(mode)
            setEnableDecoderFallback(true)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()

        player.addListener(PlayerListener())
        exoPlayer = player

        try {
            mediaSession = MediaSession.Builder(context, player)
                .setId("WatchTogetherMediaSession_${System.currentTimeMillis()}")
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun attachSurfaceView(playerView: PlayerView) {
        attachedPlayerView = playerView
        playerView.player = exoPlayer
        applyResizeMode(_state.value.resizeMode)
    }

    override fun setMedia(uri: Uri, title: String?, startPositionMs: Long) {
        val player = exoPlayer ?: return
        currentMediaUri = uri
        currentMediaTitle = title ?: uri.lastPathSegment ?: "Video"
        externalSubtitles.clear()

        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                videoTitle = currentMediaTitle,
                videoUri = uri.toString(),
                positionMs = startPositionMs,
                durationMs = 0L
            )
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(currentMediaTitle)
            .setDisplayTitle(currentMediaTitle)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }

    override fun play() {
        exoPlayer?.play()
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val validPos = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        player.seekTo(validPos)
        _state.update { it.copy(positionMs = validPos) }
    }

    override fun seekBy(offsetMs: Long) {
        val player = exoPlayer ?: return
        val newPos = (player.currentPosition + offsetMs).coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        player.seekTo(newPos)
        _state.update { it.copy(positionMs = newPos) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = clamped
        _state.update { it.copy(volume = clamped, isMuted = clamped == 0f) }
    }

    override fun setMuted(muted: Boolean) {
        exoPlayer?.volume = if (muted) 0f else _state.value.volume.coerceAtLeast(0.1f)
        _state.update { it.copy(isMuted = muted) }
    }

    override fun selectAudioTrack(track: AudioTrack) {
        val player = exoPlayer ?: return
        val tracks = currentTracks ?: return

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val id = format.id ?: "$i"
                    if (id == track.id || format.language == track.language) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                            .build()
                        _state.update { it.copy(selectedAudioTrack = track) }
                        refreshTrackLists()
                        return
                    }
                }
            }
        }
    }

    override fun selectSubtitleTrack(track: SubtitleTrack?) {
        val player = exoPlayer ?: return
        if (track == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            _state.update { it.copy(selectedSubtitleTrack = null) }
            refreshTrackLists()
            return
        }

        val tracks = currentTracks ?: return
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val id = format.id ?: "$i"
                    if (id == track.id || format.language == track.language) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                            .build()
                        _state.update { it.copy(selectedSubtitleTrack = track) }
                        refreshTrackLists()
                        return
                    }
                }
            }
        }
    }

    override fun addExternalSubtitle(uri: Uri, displayName: String) {
        val player = exoPlayer ?: return
        val mediaUri = currentMediaUri ?: return
        val currentPos = player.currentPosition

        val ext = displayName.substringAfterLast('.', "").lowercase()
        val mimeType = when (ext) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLabel(displayName)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        externalSubtitles.add(subtitleConfig)

        val metadata = MediaMetadata.Builder()
            .setTitle(currentMediaTitle)
            .setDisplayTitle(currentMediaTitle)
            .build()

        val newMediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(externalSubtitles)
            .build()

        player.setMediaItem(newMediaItem, currentPos)
        player.prepare()
        player.play()
    }

    override fun setAudioDelay(delayMs: Long) {
        _state.update { it.copy(audioDelayMs = delayMs) }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        _state.update { it.copy(subtitleDelayMs = delayMs) }
    }

    override fun setResizeMode(mode: ResizeMode) {
        _state.update { it.copy(resizeMode = mode) }
        applyResizeMode(mode)
    }

    private fun applyResizeMode(mode: ResizeMode) {
        attachedPlayerView?.let { view ->
            when (mode) {
                ResizeMode.FIT -> view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                ResizeMode.FILL -> view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                ResizeMode.ZOOM -> view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                ResizeMode.ORIGINAL -> view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                ResizeMode.RATIO_16_9 -> view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                ResizeMode.RATIO_4_3 -> view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            }
        }
    }

    override fun setControlsLocked(locked: Boolean) {
        _state.update { it.copy(isLocked = locked) }
    }

    override fun release() {
        progressJob?.cancel()
        try {
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        exoPlayer?.release()
        exoPlayer = null
        attachedPlayerView?.player = null
        attachedPlayerView = null
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null) {
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0L)
                    val buffered = player.bufferedPosition
                    _state.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = buffered
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
    }

    private fun refreshTrackLists() {
        val tracks = currentTracks ?: return
        val audioList = mutableListOf<AudioTrack>()
        val subList = mutableListOf<SubtitleTrack>()

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val id = format.id ?: "audio_${audioList.size}"
                        val lang = format.language ?: "Unknown"
                        val label = format.label ?: "$lang (Audio ${audioList.size + 1})"
                        val isSelected = group.isTrackSelected(i)
                        val track = AudioTrack(
                            id = id,
                            label = label,
                            language = format.language,
                            isSelected = isSelected,
                            channels = format.channelCount,
                            sampleRate = format.sampleRate,
                            mimeType = format.sampleMimeType
                        )
                        audioList.add(track)
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val id = format.id ?: "sub_${subList.size}"
                        val lang = format.language ?: "Unknown"
                        val label = format.label ?: "$lang (Subtitle ${subList.size + 1})"
                        val isSelected = group.isTrackSelected(i)
                        val track = SubtitleTrack(
                            id = id,
                            label = label,
                            language = format.language,
                            isSelected = isSelected,
                            isExternal = false,
                            mimeType = format.sampleMimeType
                        )
                        subList.add(track)
                    }
                }
            }
        }

        _state.update { current ->
            current.copy(
                audioTracks = audioList,
                subtitleTracks = subList,
                selectedAudioTrack = audioList.find { it.isSelected },
                selectedSubtitleTrack = subList.find { it.isSelected }
            )
        }
    }

    private inner class PlayerListener : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.update { it.copy(isLoading = false, isBuffering = true) }
                }
                Player.STATE_READY -> {
                    val dur = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isBuffering = false,
                            durationMs = dur
                        )
                    }
                    if (exoPlayer?.isPlaying == true) {
                        startProgressLoop()
                    }
                }
                Player.STATE_ENDED -> {
                    stopProgressLoop()
                    _state.update { it.copy(isPlaying = false, isBuffering = false) }
                    scope.launch {
                        _events.emit(PlayerEvent.PlaybackCompleted)
                    }
                }
                Player.STATE_IDLE -> {
                    stopProgressLoop()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressLoop()
            } else {
                stopProgressLoop()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            currentTracks = tracks
            refreshTrackLists()
        }

        override fun onPlayerError(error: PlaybackException) {
            stopProgressLoop()
            val mappedError = when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                    PlayerError.DecoderError(error.localizedMessage)
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                    PlayerError.UnsupportedCodec(error.localizedMessage)
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                    PlayerError.MissingFile(error.localizedMessage)
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                    PlayerError.CorruptedFile(error.localizedMessage)
                else ->
                    PlayerError.Unknown(error.localizedMessage ?: "Code: ${error.errorCode}")
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    isBuffering = false,
                    isPlaying = false,
                    error = mappedError
                )
            }
            scope.launch {
                _events.emit(PlayerEvent.ErrorOccurred(mappedError))
            }
        }
    }
}
