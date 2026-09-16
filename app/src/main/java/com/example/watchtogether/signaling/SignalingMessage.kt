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
        const val TYPE_REQUEST_SYNC = "REQUEST_SYNC"
        const val TYPE_REQUEST_PLAYBACK_STATE = "REQUEST_PLAYBACK_STATE"
        const val TYPE_PLAYBACK_STATE = "PLAYBACK_STATE"
        const val TYPE_MEDIA_STARTED = "MEDIA_STARTED"
        const val TYPE_REQUEST_MEDIA = "REQUEST_MEDIA"
        const val TYPE_REQUEST_FILE = "REQUEST_FILE"
        const val TYPE_FILE_TRANSFER_START = "FILE_TRANSFER_START"
        const val TYPE_FILE_TRANSFER_CHUNK = "FILE_TRANSFER_CHUNK"
        const val TYPE_FILE_TRANSFER_COMPLETE = "FILE_TRANSFER_COMPLETE"

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
            playbackSpeed: Float = 1.0f,
            initialized: Boolean = true
        ): SignalingMessage {
            val payload = JSONObject().apply {
                put("positionMs", positionMs)
                put("isPlaying", isPlaying)
                put("playing", isPlaying)
                put("paused", !isPlaying)
                put("currentTime", positionMs / 1000.0)
                put("playbackSpeed", playbackSpeed.toDouble())
                put("initialized", initialized)
                put("sentAt", System.currentTimeMillis())
            }
            return SignalingMessage(
                type = TYPE_SYNC,
                roomCode = roomCode,
                sequence = sequence,
                payload = payload
            )
        }

        fun createRequestPlaybackState(roomCode: String): SignalingMessage {
            return SignalingMessage(
                type = TYPE_REQUEST_PLAYBACK_STATE,
                roomCode = roomCode,
                payload = JSONObject()
            )
        }

        fun createPlaybackState(
            roomCode: String,
            sequence: Long,
            positionMs: Long,
            isPlaying: Boolean,
            playbackSpeed: Float = 1.0f,
            initialized: Boolean = true
        ): SignalingMessage {
            val payload = JSONObject().apply {
                put("positionMs", positionMs)
                put("currentTime", positionMs / 1000.0)
                put("isPlaying", isPlaying)
                put("playing", isPlaying)
                put("paused", !isPlaying)
                put("playbackSpeed", playbackSpeed.toDouble())
                put("initialized", initialized)
                put("sentAt", System.currentTimeMillis())
            }
            return SignalingMessage(
                type = TYPE_PLAYBACK_STATE,
                roomCode = roomCode,
                sequence = sequence,
                payload = payload
            )
        }

        fun createMediaStarted(
            roomCode: String,
            name: String,
            durationMs: Long,
            uri: String? = null,
            mimeType: String = "video/mp4",
            fileSize: Long = 0L
        ): SignalingMessage {
            val payload = JSONObject().apply {
                put("name", name)
                put("durationMs", durationMs)
                if (uri != null) {
                    put("uri", uri)
                }
                put("mimeType", mimeType)
                put("fileSize", fileSize)
            }
            return SignalingMessage(
                type = TYPE_MEDIA_STARTED,
                roomCode = roomCode,
                payload = payload
            )
        }

        fun createRequestMedia(roomCode: String): SignalingMessage {
            return SignalingMessage(
                type = TYPE_REQUEST_MEDIA,
                roomCode = roomCode,
                payload = JSONObject()
            )
        }

        fun createRequestFile(roomCode: String): SignalingMessage {
            return SignalingMessage(
                type = TYPE_REQUEST_FILE,
                roomCode = roomCode,
                payload = JSONObject()
            )
        }

        fun createFileTransferStart(
            roomCode: String,
            fileName: String,
            fileSize: Long,
            mimeType: String,
            totalChunks: Int,
            chunkSize: Int
        ): SignalingMessage {
            val payload = JSONObject().apply {
                put("fileName", fileName)
                put("fileSize", fileSize)
                put("mimeType", mimeType)
                put("totalChunks", totalChunks)
                put("chunkSize", chunkSize)
            }
            return SignalingMessage(
                type = TYPE_FILE_TRANSFER_START,
                roomCode = roomCode,
                payload = payload
            )
        }

        fun createFileTransferChunk(
            roomCode: String,
            chunkIndex: Int,
            totalChunks: Int,
            dataBase64: String
        ): SignalingMessage {
            val payload = JSONObject().apply {
                put("chunkIndex", chunkIndex)
                put("totalChunks", totalChunks)
                put("data", dataBase64)
            }
            return SignalingMessage(
                type = TYPE_FILE_TRANSFER_CHUNK,
                roomCode = roomCode,
                payload = payload
            )
        }

        fun createFileTransferComplete(
            roomCode: String,
            fileName: String,
            totalChunks: Int
        ): SignalingMessage {
            val payload = JSONObject().apply {
                put("fileName", fileName)
                put("totalChunks", totalChunks)
            }
            return SignalingMessage(
                type = TYPE_FILE_TRANSFER_COMPLETE,
                roomCode = roomCode,
                payload = payload
            )
        }

        fun createRequestSync(roomCode: String): SignalingMessage {
            return SignalingMessage(
                type = TYPE_REQUEST_SYNC,
                roomCode = roomCode,
                payload = JSONObject()
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
