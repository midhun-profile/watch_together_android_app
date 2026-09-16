package com.example.watchtogether.room

import android.util.Log
import com.example.watchtogether.model.CreateRoomResponse
import com.example.watchtogether.model.JoinRoomResponse
import com.example.watchtogether.model.RoomRole
import com.example.watchtogether.model.RoomSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface RoomRepository {
    suspend fun createRoom(): Result<CreateRoomResponse>
    suspend fun joinRoom(roomCode: String): Result<JoinRoomResponse>
    suspend fun getRoomStatus(roomCode: String): Result<JSONObject>
    suspend fun leaveRoom(roomCode: String): Result<Unit>
    fun getServerBaseUrl(): String
    fun setServerBaseUrl(url: String)
}

class RoomRepositoryImpl(
    private var serverBaseUrl: String = "https://watch-together-android-app-1.onrender.com"
) : RoomRepository {

    companion object {
        private const val TAG = "RoomRepository"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Local fallback coordinator for peer/offline testing
    private val localRooms = ConcurrentHashMap<String, RoomSession>()
    private val secureRandom = SecureRandom()

    override fun getServerBaseUrl(): String = serverBaseUrl

    override fun setServerBaseUrl(url: String) {
        serverBaseUrl = url.trimEnd('/')
    }

    override suspend fun createRoom(): Result<CreateRoomResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        try {
            val request = Request.Builder()
                .url("$serverBaseUrl/api/rooms")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val resp = CreateRoomResponse(
                    roomId = json.optString("roomId"),
                    roomCode = json.optString("roomCode"),
                    role = RoomRole.HOST,
                    expiresAt = json.optString("expiresAt")
                )
                // Cache locally as well
                localRooms[resp.roomCode] = RoomSession(
                    roomCode = resp.roomCode,
                    role = RoomRole.HOST,
                    roomId = resp.roomId,
                    expiresAt = resp.expiresAt
                )
                return@withContext Result.success(resp)
            } else {
                Log.w(TAG, "Backend room create returned HTTP ${response.code}")
                lastException = Exception("Server error (${response.code}). Please try again.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend unreachable (${e.message})")
            lastException = e
        }

        // If server failed, check if we should return failure or local fallback
        if (serverBaseUrl.startsWith("http://localhost") || serverBaseUrl.startsWith("http://127.0.0.1")) {
            // Local development fallback
            val generatedCode = generateLocalRoomCode()
            val session = RoomSession(
                roomCode = generatedCode,
                role = RoomRole.HOST,
                roomId = "local_${System.currentTimeMillis()}"
            )
            localRooms[generatedCode] = session

            return@withContext Result.success(
                CreateRoomResponse(
                    roomId = session.roomId,
                    roomCode = session.roomCode,
                    role = RoomRole.HOST,
                    expiresAt = "24h"
                )
            )
        }

        // Return clear failure to user rather than silent offline room
        Result.failure(lastException ?: Exception("Failed to reach server. Please check internet connection."))
    }

    override suspend fun joinRoom(roomCode: String): Result<JoinRoomResponse> = withContext(Dispatchers.IO) {
        val normalizedCode = roomCode.trim().uppercase()
        if (normalizedCode.length != 6) {
            return@withContext Result.failure(IllegalArgumentException("Room code must be exactly 6 characters"))
        }

        var networkError: Exception? = null
        try {
            val request = Request.Builder()
                .url("$serverBaseUrl/api/rooms/$normalizedCode/join")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val resp = JoinRoomResponse(
                    roomId = json.optString("roomId"),
                    roomCode = json.optString("roomCode", normalizedCode),
                    role = RoomRole.VIEWER,
                    expiresAt = json.optString("expiresAt")
                )
                return@withContext Result.success(resp)
            } else if (response.code == 404) {
                // If backend specifically returned 404, check local memory before rejecting
                if (!localRooms.containsKey(normalizedCode)) {
                    return@withContext Result.failure(Exception("Room not found. Check the code and try again."))
                }
            } else if (response.code == 409) {
                return@withContext Result.failure(Exception("Room is full. Maximum 2 participants allowed."))
            } else if (response.code == 410) {
                return@withContext Result.failure(Exception("Room has expired."))
            } else {
                networkError = Exception("Server error (${response.code}).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend unreachable (${e.message}), checking local coordinator")
            networkError = e
        }

        // Check local sessions (e.g. for offline or single-device tests)
        val local = localRooms[normalizedCode]
        if (local != null) {
            return@withContext Result.success(
                JoinRoomResponse(
                    roomId = local.roomId,
                    roomCode = normalizedCode,
                    role = RoomRole.VIEWER,
                    expiresAt = "24h"
                )
            )
        }

        Result.failure(networkError ?: Exception("Room not found. Check the code and try again."))
    }

    override suspend fun getRoomStatus(roomCode: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverBaseUrl/api/rooms/$roomCode")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                return@withContext Result.success(JSONObject(body))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get room status: ${e.message}")
        }
        Result.failure(Exception("Unable to fetch room status"))
    }

    override suspend fun leaveRoom(roomCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        localRooms.remove(roomCode.trim().uppercase())
        try {
            val request = Request.Builder()
                .url("$serverBaseUrl/api/rooms/$roomCode")
                .delete()
                .build()
            httpClient.newCall(request).execute()
        } catch (_: Exception) {
        }
        Result.success(Unit)
    }

    private fun generateLocalRoomCode(): String {
        val sb = StringBuilder(6)
        for (i in 0 until 6) {
            val idx = secureRandom.nextInt(CODE_CHARS.length)
            sb.append(CODE_CHARS[idx])
        }
        return sb.toString()
    }
}
