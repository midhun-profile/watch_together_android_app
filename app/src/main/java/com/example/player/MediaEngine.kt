package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.domain.player.AspectRatioMode
import com.example.domain.player.HardwareAcceleration
import com.example.domain.player.TrackInfo
import com.example.domain.player.TrackType
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

class MediaEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "MediaEngine"
    }

    interface EngineListener {
        fun onPlaying()
        fun onPaused()
        fun onStopped()
        fun onBuffering(bufferPercent: Float)
        fun onTimeChanged(currentTimeMs: Long)
        fun onLengthChanged(lengthMs: Long)
        fun onTracksChanged()
        fun onEndReached()
        fun onError(message: String)
    }

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentHwAcceleration: HardwareAcceleration = HardwareAcceleration.AUTOMATIC
    private var attachedLayout: VLCVideoLayout? = null
    private var listener: EngineListener? = null
    private var currentMediaUri: Uri? = null

    init {
        initLibVlc(HardwareAcceleration.AUTOMATIC)
    }

    fun setListener(engineListener: EngineListener?) {
        this.listener = engineListener
    }

    private fun initLibVlc(hw: HardwareAcceleration) {
        currentHwAcceleration = hw
        try {
            val options = ArrayList<String>().apply {
                add("--no-drop-late-frames")
                add("--no-skip-frames")
                when (hw) {
                    HardwareAcceleration.ENABLED -> {
                        add("--codec=mediacodec_ndk,mediacodec_jni,all")
                    }
                    HardwareAcceleration.DISABLED -> {
                        add("--codec=all")
                    }
                    HardwareAcceleration.AUTOMATIC -> {
                        // Standard default LibVLC behavior
                    }
                }
            }
            libVLC?.release()
            libVLC = LibVLC(appContext, options)
            setupMediaPlayer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LibVLC", e)
            listener?.onError("Failed to initialize video engine: ${e.localizedMessage}")
        }
    }

    private fun setupMediaPlayer() {
        val vlc = libVLC ?: return
        mediaPlayer?.let {
            it.stop()
            it.detachViews()
            it.release()
        }
        val mp = MediaPlayer(vlc)
        mp.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> listener?.onPlaying()
                MediaPlayer.Event.Paused -> listener?.onPaused()
                MediaPlayer.Event.Stopped -> listener?.onStopped()
                MediaPlayer.Event.Buffering -> listener?.onBuffering(event.buffering)
                MediaPlayer.Event.TimeChanged -> listener?.onTimeChanged(event.timeChanged)
                MediaPlayer.Event.LengthChanged -> listener?.onLengthChanged(event.lengthChanged)
                MediaPlayer.Event.ESAdded,
                MediaPlayer.Event.ESDeleted,
                MediaPlayer.Event.ESSelected -> listener?.onTracksChanged()
                MediaPlayer.Event.EndReached -> listener?.onEndReached()
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "LibVLC encountered error event")
                    listener?.onError("Playback error: unable to play media file.")
                }
            }
        }
        mediaPlayer = mp
        attachedLayout?.let {
            mp.attachViews(it, null, true, false)
        }
    }

    fun attachLayout(layout: VLCVideoLayout) {
        attachedLayout = layout
        mediaPlayer?.attachViews(layout, null, true, false)
    }

    fun detachLayout() {
        mediaPlayer?.detachViews()
        attachedLayout = null
    }

    fun loadMedia(uri: Uri, startPositionMs: Long = 0L) {
        val vlc = libVLC ?: return
        currentMediaUri = uri
        try {
            val media = Media(vlc, uri).apply {
                when (currentHwAcceleration) {
                    HardwareAcceleration.ENABLED -> setHWDecoderEnabled(true, true)
                    HardwareAcceleration.DISABLED -> setHWDecoderEnabled(false, false)
                    HardwareAcceleration.AUTOMATIC -> setHWDecoderEnabled(true, false)
                }
            }
            mediaPlayer?.media = media
            media.release()

            if (startPositionMs > 0) {
                mediaPlayer?.time = startPositionMs
            }
            mediaPlayer?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading media: $uri", e)
            listener?.onError("Failed to load video: ${e.localizedMessage}")
        }
    }

    fun play() {
        try {
            mediaPlayer?.play()
        } catch (e: Exception) {
            Log.e(TAG, "play() error", e)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "pause() error", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stop() error", e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            val mp = mediaPlayer ?: return
            val length = mp.length
            if (length > 0) {
                val clamped = positionMs.coerceIn(0L, length)
                mp.time = clamped
            } else {
                mp.time = positionMs.coerceAtLeast(0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "seekTo() error", e)
        }
    }

    fun getCurrentPosition(): Long {
        return mediaPlayer?.time ?: 0L
    }

    fun getDuration(): Long {
        return mediaPlayer?.length ?: 0L
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    fun setPlaybackRate(rate: Float) {
        try {
            mediaPlayer?.rate = rate
        } catch (e: Exception) {
            Log.e(TAG, "setPlaybackRate error", e)
        }
    }

    fun setVolume(volume: Int) {
        try {
            mediaPlayer?.volume = volume.coerceIn(0, 100)
        } catch (e: Exception) {
            Log.e(TAG, "setVolume error", e)
        }
    }

    fun getVolume(): Int {
        return mediaPlayer?.volume ?: 100
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        try {
            val mp = mediaPlayer ?: return
            when (mode) {
                AspectRatioMode.FIT -> {
                    mp.aspectRatio = null
                    mp.scale = 0f
                }
                AspectRatioMode.FILL -> {
                    mp.aspectRatio = null
                    mp.scale = 1.35f
                }
                AspectRatioMode.ORIGINAL -> {
                    mp.aspectRatio = null
                    mp.scale = 1.0f
                }
                AspectRatioMode.RATIO_16_9 -> {
                    mp.aspectRatio = "16:9"
                }
                AspectRatioMode.RATIO_4_3 -> {
                    mp.aspectRatio = "4:3"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setAspectRatio error", e)
        }
    }

    fun setHardwareAcceleration(hw: HardwareAcceleration) {
        if (currentHwAcceleration == hw) return
        val currentUri = currentMediaUri
        val currentTime = getCurrentPosition()
        val wasPlaying = isPlaying()
        initLibVlc(hw)
        if (currentUri != null) {
            loadMedia(currentUri, currentTime)
            if (!wasPlaying) {
                pause()
            }
        }
    }

    fun getAudioTracks(): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        try {
            val mp = mediaPlayer ?: return emptyList()
            val descriptions = mp.audioTracks ?: return emptyList()
            val currentTrackId = mp.audioTrack
            for (desc in descriptions) {
                tracks.add(
                    TrackInfo(
                        id = desc.id,
                        name = desc.name ?: "Track ${desc.id}",
                        selected = desc.id == currentTrackId,
                        type = TrackType.AUDIO
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAudioTracks error", e)
        }
        return tracks
    }

    fun setAudioTrack(trackId: Int) {
        try {
            mediaPlayer?.audioTrack = trackId
        } catch (e: Exception) {
            Log.e(TAG, "setAudioTrack error", e)
        }
    }

    fun setAudioDelay(delayMs: Long) {
        try {
            // delay in microseconds for LibVLC
            mediaPlayer?.setAudioDelay(delayMs * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "setAudioDelay error", e)
        }
    }

    fun getSubtitleTracks(): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        try {
            val mp = mediaPlayer ?: return emptyList()
            val descriptions = mp.spuTracks ?: return emptyList()
            val currentTrackId = mp.spuTrack
            for (desc in descriptions) {
                tracks.add(
                    TrackInfo(
                        id = desc.id,
                        name = desc.name ?: "Subtitle ${desc.id}",
                        selected = desc.id == currentTrackId,
                        type = TrackType.SUBTITLE
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSubtitleTracks error", e)
        }
        return tracks
    }

    fun setSubtitleTrack(trackId: Int) {
        try {
            mediaPlayer?.spuTrack = trackId
        } catch (e: Exception) {
            Log.e(TAG, "setSubtitleTrack error", e)
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        try {
            // delay in microseconds for LibVLC
            mediaPlayer?.setSpuDelay(delayMs * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "setSubtitleDelay error", e)
        }
    }

    fun addExternalSubtitle(uri: Uri) {
        try {
            val mp = mediaPlayer ?: return
            mp.addSlave(IMedia.Slave.Type.Subtitle, uri, true)
        } catch (e: Exception) {
            Log.e(TAG, "addExternalSubtitle error", e)
            listener?.onError("Failed to load external subtitle: ${e.localizedMessage}")
        }
    }

    fun release() {
        try {
            mediaPlayer?.let {
                it.stop()
                it.detachViews()
                it.release()
            }
            mediaPlayer = null
            libVLC?.release()
            libVLC = null
        } catch (e: Exception) {
            Log.e(TAG, "release error", e)
        }
    }
}
