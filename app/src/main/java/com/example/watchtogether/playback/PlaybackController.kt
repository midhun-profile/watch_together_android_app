package com.example.watchtogether.playback

import android.util.Log
import com.example.data.local.player.VideoPlayer

/**
 * Minimal playback control abstraction separating WatchTogether from the underlying player engine.
 * As defined in Section 19 of the specification.
 */
interface PlaybackController {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun isPlaying(): Boolean
}

/**
 * Clean adapter linking the existing VideoPlayer engine to the PlaybackController abstraction
 * without modifying any player internals.
 */
class ExistingPlayerAdapter(
    private val videoPlayer: VideoPlayer
) : PlaybackController {

    override fun play() {
        try {
            videoPlayer.play()
            Log.d("[VIDEO]", "play() succeeded")
        } catch (e: Exception) {
            Log.e("[VIDEO]", "play() rejected", e)
        }
    }

    override fun pause() {
        videoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        videoPlayer.seekTo(positionMs)
    }

    override fun getCurrentPosition(): Long {
        return videoPlayer.state.value.positionMs
    }

    override fun getDuration(): Long {
        return videoPlayer.state.value.durationMs
    }

    override fun isPlaying(): Boolean {
        return videoPlayer.state.value.isPlaying
    }
}
