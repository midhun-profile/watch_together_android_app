package com.example.watchtogether

import com.example.watchtogether.playback.PlaybackController
import com.example.watchtogether.signaling.SignalingClient
import com.example.watchtogether.signaling.SignalingMessage
import com.example.watchtogether.sync.PlaybackSyncManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchTogetherTests {

    class MockPlaybackController : PlaybackController {
        var isPlayingState = false
        var currentPos = 0L
        var mockDuration = 120_000L
        var seekCalls = mutableListOf<Long>()

        override fun play() {
            isPlayingState = true
        }

        override fun pause() {
            isPlayingState = false
        }

        override fun seekTo(positionMs: Long) {
            currentPos = positionMs
            seekCalls.add(positionMs)
        }

        override fun getCurrentPosition(): Long = currentPos
        override fun getDuration(): Long = mockDuration
        override fun isPlaying(): Boolean = isPlayingState
    }

    @Test
    fun testRoomCodeAlphabet_has32UnambiguousChars() {
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        assertEquals(32, alphabet.length)
        assertFalse(alphabet.contains('0'))
        assertFalse(alphabet.contains('O'))
        assertFalse(alphabet.contains('1'))
        assertFalse(alphabet.contains('I'))
    }

    @Test
    fun testSignalingMessage_serializationRoundTrip() {
        val playMsg = SignalingMessage.createPlay(
            roomCode = "K7P4XM",
            sequence = 42L,
            positionMs = 15000L
        )

        val jsonStr = playMsg.toJson()
        val parsed = SignalingMessage.fromJson(jsonStr)

        assertNotNull(parsed)
        assertEquals("PLAY", parsed?.type)
        assertEquals("K7P4XM", parsed?.roomCode)
        assertEquals(42L, parsed?.sequence)
        assertEquals(15000L, parsed?.payload?.optLong("positionMs"))
    }

    @Test
    fun testSignalingMessage_syncMessage() {
        val syncMsg = SignalingMessage.createSync(
            roomCode = "K7P4XM",
            sequence = 10L,
            positionMs = 50000L,
            isPlaying = true,
            playbackSpeed = 1.0f
        )

        val parsed = SignalingMessage.fromJson(syncMsg.toJson())
        assertNotNull(parsed)
        assertEquals("SYNC", parsed?.type)
        assertEquals(50000L, parsed?.payload?.optLong("positionMs"))
        assertTrue(parsed?.payload?.optBoolean("isPlaying") == true)
    }

    @Test
    fun testPlaybackSyncManager_viewerDriftCorrection() {
        val mockPlayer = MockPlaybackController()
        val client = SignalingClient()
        val syncManager = PlaybackSyncManager(client, mockPlayer)
        syncManager.start("K7P4XM", com.example.watchtogether.model.RoomRole.VIEWER)

        // Case 1: Drift > 1000ms triggers seekTo
        mockPlayer.currentPos = 5000L
        val now = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("positionMs", 10000L)
            put("isPlaying", true)
            put("playbackSpeed", 1.0)
            put("sentAt", now)
        }
        val syncMsg = SignalingMessage(
            type = SignalingMessage.TYPE_SYNC,
            roomCode = "K7P4XM",
            sequence = 1L,
            payload = payload
        )

        syncManager.handleIncomingSignaling(syncMsg)

        // Player should have sought to ~10000ms
        assertTrue(mockPlayer.seekCalls.isNotEmpty())
        assertEquals(10000L, mockPlayer.seekCalls.last())
        assertTrue(mockPlayer.isPlayingState)

        // Case 2: Stale sequence numbers are ignored
        mockPlayer.seekCalls.clear()
        val staleMsg = SignalingMessage(
            type = SignalingMessage.TYPE_SYNC,
            roomCode = "K7P4XM",
            sequence = 1L, // Same sequence as before
            payload = payload
        )
        syncManager.handleIncomingSignaling(staleMsg)
        assertTrue("Stale sequence must not trigger seek", mockPlayer.seekCalls.isEmpty())
    }
}
