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
        assertTrue(mockPlayer.seekCalls.last() in 10000L..12000L)
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

    @Test
    fun testRoomUiState_hostRoleEvaluation() {
        val hostSession = com.example.watchtogether.model.RoomSession(
            roomCode = "ABCDEF",
            role = com.example.watchtogether.model.RoomRole.HOST
        )
        val hostState = com.example.watchtogether.model.RoomUiState(
            roomCode = "ABCDEF",
            role = com.example.watchtogether.model.RoomRole.HOST,
            currentSession = hostSession
        )
        assertTrue("Host UI state must evaluate isHost to true", hostState.isHost)

        val viewerSession = com.example.watchtogether.model.RoomSession(
            roomCode = "ABCDEF",
            role = com.example.watchtogether.model.RoomRole.VIEWER
        )
        val viewerState = com.example.watchtogether.model.RoomUiState(
            roomCode = "ABCDEF",
            role = com.example.watchtogether.model.RoomRole.VIEWER,
            currentSession = viewerSession
        )
        assertFalse("Viewer UI state must evaluate isHost to false", viewerState.isHost)
    }

    @Test
    fun testWebRtcManager_roleValidation() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val client = SignalingClient()
        val rtcManager = com.example.watchtogether.webrtc.WebRtcManagerImpl(context, client)

        // As HOST: cannot create answer (silently rejected with warning, iceState unchanged)
        rtcManager.setRole(com.example.watchtogether.model.RoomRole.HOST)
        rtcManager.createAnswer("ABCDEF")
        assertEquals("NEW", rtcManager.getIceConnectionState())

        // As VIEWER: cannot create offer (silently rejected with warning, iceState unchanged)
        rtcManager.setRole(com.example.watchtogether.model.RoomRole.VIEWER)
        rtcManager.createOffer("ABCDEF")
        assertEquals("NEW", rtcManager.getIceConnectionState())
    }

    @Test
    fun testLocalSignalingHub_roleDispatching() {
        var viewerReceived: SignalingMessage? = null
        var hostReceived: SignalingMessage? = null

        com.example.watchtogether.signaling.LocalSignalingHub.register("XYZ123", com.example.watchtogether.model.RoomRole.VIEWER) { msg ->
            viewerReceived = msg
        }
        com.example.watchtogether.signaling.LocalSignalingHub.register("XYZ123", com.example.watchtogether.model.RoomRole.HOST) { msg ->
            hostReceived = msg
        }

        val hostMsg = SignalingMessage(
            type = SignalingMessage.TYPE_PLAY,
            roomCode = "XYZ123",
            sequence = 1L
        )

        // HOST dispatches: must be received by VIEWER, NOT by HOST itself
        com.example.watchtogether.signaling.LocalSignalingHub.dispatch(
            roomCode = "XYZ123",
            senderRole = com.example.watchtogether.model.RoomRole.HOST,
            message = hostMsg
        )

        assertNotNull("Viewer should receive host message", viewerReceived)
        assertEquals("XYZ123", viewerReceived?.roomCode)
        org.junit.Assert.assertNull("Host should not receive its own dispatched message", hostReceived)

        // Clean up
        com.example.watchtogether.signaling.LocalSignalingHub.unregister("XYZ123", com.example.watchtogether.model.RoomRole.VIEWER)
        com.example.watchtogether.signaling.LocalSignalingHub.unregister("XYZ123", com.example.watchtogether.model.RoomRole.HOST)
    }

    @Test
    fun testFileTransferSignalingMessages() {
        val startMsg = SignalingMessage.createFileTransferStart(
            roomCode = "ABC123",
            fileName = "sample_movie.mp4",
            fileSize = 1048576L,
            mimeType = "video/mp4",
            totalChunks = 32,
            chunkSize = 32768
        )
        val parsedStart = SignalingMessage.fromJson(startMsg.toJson())
        assertNotNull(parsedStart)
        assertEquals(SignalingMessage.TYPE_FILE_TRANSFER_START, parsedStart?.type)
        assertEquals("sample_movie.mp4", parsedStart?.payload?.optString("fileName"))
        assertEquals(32, parsedStart?.payload?.optInt("totalChunks"))

        val chunkMsg = SignalingMessage.createFileTransferChunk(
            roomCode = "ABC123",
            chunkIndex = 5,
            totalChunks = 32,
            dataBase64 = "aGVsbG8gd29ybGQ="
        )
        val parsedChunk = SignalingMessage.fromJson(chunkMsg.toJson())
        assertNotNull(parsedChunk)
        assertEquals(SignalingMessage.TYPE_FILE_TRANSFER_CHUNK, parsedChunk?.type)
        assertEquals(5, parsedChunk?.payload?.optInt("chunkIndex"))
        assertEquals("aGVsbG8gd29ybGQ=", parsedChunk?.payload?.optString("data"))

        val completeMsg = SignalingMessage.createFileTransferComplete(
            roomCode = "ABC123",
            fileName = "sample_movie.mp4",
            totalChunks = 32
        )
        val parsedComplete = SignalingMessage.fromJson(completeMsg.toJson())
        assertNotNull(parsedComplete)
        assertEquals(SignalingMessage.TYPE_FILE_TRANSFER_COMPLETE, parsedComplete?.type)

        val reqFileMsg = SignalingMessage.createRequestFile("ABC123")
        val parsedReqFile = SignalingMessage.fromJson(reqFileMsg.toJson())
        assertNotNull(parsedReqFile)
        assertEquals(SignalingMessage.TYPE_REQUEST_FILE, parsedReqFile?.type)

        val reqMediaMsg = SignalingMessage.createRequestMedia("ABC123")
        val parsedReqMedia = SignalingMessage.fromJson(reqMediaMsg.toJson())
        assertNotNull(parsedReqMedia)
        assertEquals(SignalingMessage.TYPE_REQUEST_MEDIA, parsedReqMedia?.type)
    }

    @Test
    fun testFileTransferReconstruction() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val client = SignalingClient()
        val transferManager = com.example.watchtogether.transfer.FileTransferManager(context, client)
        transferManager.start("TEST01", com.example.watchtogether.model.RoomRole.VIEWER)

        var receivedUri: android.net.Uri? = null
        var receivedName: String? = null
        var completed = false

        transferManager.setListener(object : com.example.watchtogether.transfer.FileTransferListener {
            override fun onTransferProgress(progress: Float, statusText: String) {}
            override fun onFileReceived(localUri: android.net.Uri, fileName: String, mimeType: String) {
                receivedUri = localUri
                receivedName = fileName
                completed = true
            }
            override fun onError(error: String) {}
        })

        // Simulate START
        val sampleData = "WatchTogether test movie payload chunk bytes content"
        val bytes = sampleData.toByteArray(Charsets.UTF_8)
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        val startMsg = SignalingMessage.createFileTransferStart(
            roomCode = "TEST01",
            fileName = "test_movie.mp4",
            fileSize = bytes.size.toLong(),
            mimeType = "video/mp4",
            totalChunks = 1,
            chunkSize = 32768
        )
        transferManager.handleSignalingMessage(startMsg)

        // Simulate CHUNK 0
        val chunkMsg = SignalingMessage.createFileTransferChunk(
            roomCode = "TEST01",
            chunkIndex = 0,
            totalChunks = 1,
            dataBase64 = b64
        )
        transferManager.handleSignalingMessage(chunkMsg)

        // Simulate COMPLETE
        val completeMsg = SignalingMessage.createFileTransferComplete(
            roomCode = "TEST01",
            fileName = "test_movie.mp4",
            totalChunks = 1
        )
        transferManager.handleSignalingMessage(completeMsg)

        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("File transfer must complete successfully", completed)
        assertNotNull(receivedUri)
        assertEquals("test_movie.mp4", receivedName)

        // Verify content matches
        val reconstructedFile = java.io.File(receivedUri!!.path!!)
        assertTrue(reconstructedFile.exists())
        assertEquals(sampleData, reconstructedFile.readText(Charsets.UTF_8))
        reconstructedFile.delete()
    }
}
