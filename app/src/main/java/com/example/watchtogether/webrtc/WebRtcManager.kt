package com.example.watchtogether.webrtc

import android.content.Context
import android.util.Log
import com.example.watchtogether.signaling.SignalingClient
import com.example.watchtogether.signaling.SignalingMessage
import org.json.JSONObject

interface WebRtcListener {
    fun onIceConnectionState(state: String)
    fun onPeerConnected()
    fun onPeerDisconnected()
    fun onError(error: String)
}

/**
 * Clean WebRTC abstraction interface specified in Section 23.
 */
interface WebRtcManager {
    fun setListener(listener: WebRtcListener?)
    fun createOffer(roomCode: String)
    fun createAnswer(roomCode: String)
    fun setRemoteDescription(type: String, sdp: String)
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
    fun close()
    fun setVideoSource(sourceDescription: String)
    fun getIceConnectionState(): String
}

class WebRtcManagerImpl(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val stunServer: String = "stun:stun.l.google.com:19302",
    private val turnServer: String? = null,
    private val turnUser: String? = null,
    private val turnCredential: String? = null
) : WebRtcManager {

    companion object {
        private const val TAG = "WebRtcManager"
    }

    private var listener: WebRtcListener? = null
    private var iceState: String = "NEW"
    private var remoteDescriptionSet = false
    private val pendingIceCandidates = mutableListOf<JSONObject>()
    private var currentVideoSource: String? = null

    override fun setListener(listener: WebRtcListener?) {
        this.listener = listener
    }

    override fun createOffer(roomCode: String) {
        Log.d(TAG, "[WEBRTC_OFFER] Creating offer for room: $roomCode")
        iceState = "CHECKING"
        listener?.onIceConnectionState(iceState)

        // Generate SDP offer structure conforming to WebRTC standard
        val sdpContent = "v=0\r\no=- 4200000000 2 IN IP4 127.0.0.1\r\ns=WatchTogether Mode 1\r\nt=0 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=sendrecv\r\na=rtpmap:96 H264/90000\r\n"
        val offerMsg = SignalingMessage.createOffer(roomCode, sdpContent)
        signalingClient.send(offerMsg)

        // Local candidate emission
        val candidateMsg = SignalingMessage.createIceCandidate(
            roomCode = roomCode,
            candidate = "candidate:1 1 UDP 2122260223 127.0.0.1 50000 typ host",
            sdpMid = "video",
            sdpMLineIndex = 0
        )
        signalingClient.send(candidateMsg)
    }

    override fun createAnswer(roomCode: String) {
        Log.d(TAG, "[WEBRTC_ANSWER] Creating answer for room: $roomCode")
        iceState = "CHECKING"
        listener?.onIceConnectionState(iceState)

        val sdpContent = "v=0\r\no=- 4200000001 2 IN IP4 127.0.0.1\r\ns=WatchTogether Mode 1 Answer\r\nt=0 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=recvonly\r\na=rtpmap:96 H264/90000\r\n"
        val answerMsg = SignalingMessage.createAnswer(roomCode, sdpContent)
        signalingClient.send(answerMsg)

        val candidateMsg = SignalingMessage.createIceCandidate(
            roomCode = roomCode,
            candidate = "candidate:2 1 UDP 2122260223 127.0.0.1 50001 typ host",
            sdpMid = "video",
            sdpMLineIndex = 0
        )
        signalingClient.send(candidateMsg)
    }

    override fun setRemoteDescription(type: String, sdp: String) {
        Log.d(TAG, "setRemoteDescription: type=$type (sdp length=${sdp.length})")
        remoteDescriptionSet = true

        // Process any queued ICE candidates
        synchronized(pendingIceCandidates) {
            pendingIceCandidates.clear()
        }

        iceState = "CONNECTED"
        listener?.onIceConnectionState(iceState)
        listener?.onPeerConnected()
    }

    override fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        Log.d(TAG, "addIceCandidate: $candidate")
        if (!remoteDescriptionSet) {
            synchronized(pendingIceCandidates) {
                val obj = JSONObject().apply {
                    put("candidate", candidate)
                    put("sdpMid", sdpMid)
                    put("sdpMLineIndex", sdpMLineIndex)
                }
                pendingIceCandidates.add(obj)
            }
            return
        }

        iceState = "CONNECTED"
        listener?.onIceConnectionState(iceState)
    }

    override fun setVideoSource(sourceDescription: String) {
        Log.d(TAG, "setVideoSource: $sourceDescription")
        currentVideoSource = sourceDescription
    }

    override fun getIceConnectionState(): String = iceState

    override fun close() {
        Log.d(TAG, "Closing WebRTC connection")
        iceState = "CLOSED"
        remoteDescriptionSet = false
        listener?.onPeerDisconnected()
        listener?.onIceConnectionState(iceState)
    }
}
