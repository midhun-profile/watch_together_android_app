package com.example.watchtogether.signaling

import org.json.JSONObject

data class SignalingMessage(
    val type: String,
    val roomCode: String = "",
    val sequence: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: JSONObject = JSONObject()
) {
    companion object {
        const val TYPE_ROOM_JOINED = "ROOM_JOINED"
        const val TYPE_USER_JOINED = "USER_JOINED"
        const val TYPE_USER_LEFT = "USER_LEFT"
        const val TYPE_HOST_DISCONNECTED = "HOST_DISCONNECTED"
        const val TYPE_ROOM_EXPIRED = "ROOM_EXPIRED"

        const val TYPE_WEBRTC_OFFER = "WEBRTC_OFFER"
        const val TYPE_WEBRTC_ANSWER = "WEBRTC_ANSWER"
        const val TYPE_ICE_CANDIDATE = "ICE_CANDIDATE"

        const val TYPE_PLAY = "PLAY"
        const val TYPE_PAUSE = "PAUSE"
        const val TYPE_SEEK = "SEEK"
        const val TYPE_SYNC = "SYNC"
        const val TYPE_MEDIA_STARTED = "MEDIA_STARTED"

        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"
        const val TYPE_ERROR = "ERROR"

        fun fromJson(jsonStr: String): SignalingMessage? {
            return try {
                val obj = JSONObject(jsonStr)
                val type = obj.optString("type", "")
                val roomCode = obj.optString("roomCode", "")
                val sequence = obj.optLong("sequence", 0L)
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val payload = obj.optJSONObject("payload") ?: JSONObject()
                SignalingMessage(
                    type = type,
                    roomCode = roomCode,
                    sequence = sequence,
                    timestamp = timestamp,
                    payload = payload
                )
            } catch (e: Exception) {
                null
            }
        }

        fun createPlay(roomCode: String, sequence: Long, positionMs: Long): SignalingMessage {
            val payload = JSONObject().apply {
                put("positionMs", positionMs)
                put("sentAt", System.currentTimeMillis())
            }
            return SignalingMessage(
                type = TYPE_PLAY,
                roomCode = roomCode,
                sequence = sequence,
                payload = payload
            )
        }

        fun createPause(roomCode: String, sequence: Long, positionMs: Long): SignalingMessage {
            val payload = JSONObject().apply {
                put("positionMs", positionMs)
                put("sentAt", System.currentTimeMillis())
            }
            return SignalingMessage(
                type = TYPE_PAUSE,
                roomCode = roomCode,
                sequence = sequence,
                payload = payload
            )
        }

        fun createSeek(roomCode: String, sequence: Long, targetPositionMs: Long): SignalingMessage {
            val payload = JSONObject().apply {
                put("targetPositionMs", targetPositionMs)
                put("sentAt", System.currentTimeMillis())
            }
            return SignalingMessage(
                type = TYPE_SEEK,
                roomCode = roomCode,
                sequence = sequence,
                payload = payload
            )
        }

        fun createSync(
            roomCode: String,
            sequence: Long,
            positionMs: Long,
            isPlaying: Boolean,
            playbackSpeed: Float
        ): SignalingMessage {
            val payload = JSONObject().apply {
                put("positionMs", positionMs)
                put("isPlaying", isPlaying)
                put("playbackSpeed", playbackSpeed.toDouble())
                put("sentAt", System.currentTimeMillis())
            }
            return SignalingMessage(
                type = TYPE_SYNC,
                roomCode = roomCode,
                sequence = sequence,
                payload = payload
            )
        }

        fun createMediaStarted(roomCode: String, name: String, durationMs: Long): SignalingMessage {
            val payload = JSONObject().apply {
                put("name", name)
                put("durationMs", durationMs)
            }
            return SignalingMessage(
                type = TYPE_MEDIA_STARTED,
                roomCode = roomCode,
                payload = payload
            )
        }

        fun createOffer(roomCode: String, sdp: String): SignalingMessage {
            val payload = JSONObject().apply {
                put("sdp", sdp)
                put("type", "offer")
            }
            return SignalingMessage(
                type = TYPE_WEBRTC_OFFER,
                roomCode = roomCode,
                payload = payload
            )
        }

        fun createAnswer(roomCode: String, sdp: String): SignalingMessage {
            val payload = JSONObject().apply {
                put("sdp", sdp)
                put("type", "answer")
            }
            return SignalingMessage(
                type = TYPE_WEBRTC_ANSWER,
                roomCode = roomCode,
                payload = payload
            )
        }

        fun createIceCandidate(
            roomCode: String,
            candidate: String,
            sdpMid: String?,
            sdpMLineIndex: Int
        ): SignalingMessage {
            val payload = JSONObject().apply {
                put("candidate", candidate)
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
            }
            return SignalingMessage(
                type = TYPE_ICE_CANDIDATE,
                roomCode = roomCode,
                payload = payload
            )
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", type)
        obj.put("roomCode", roomCode)
        obj.put("sequence", sequence)
        obj.put("timestamp", timestamp)
        obj.put("payload", payload)
        return obj.toString()
    }
}
