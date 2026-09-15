package com.example.watchtogether.signaling

import android.util.Log
import com.example.watchtogether.model.ConnectionState
import com.example.watchtogether.model.RoomRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

interface SignalingListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onMessageReceived(message: SignalingMessage)
    fun onError(error: String)
}

/**
 * In-memory local signaling broker for local/offline fallback testing.
 */
object LocalSignalingHub {
    private val channelListeners = ConcurrentHashMap<String, MutableSet<(SignalingMessage) -> Unit>>()

    fun register(roomCode: String, listener: (SignalingMessage) -> Unit) {
        val key = roomCode.trim().uppercase()
        channelListeners.computeIfAbsent(key) { CopyOnWriteArraySet() }.add(listener)
    }

    fun unregister(roomCode: String, listener: (SignalingMessage) -> Unit) {
        val key = roomCode.trim().uppercase()
        channelListeners[key]?.remove(listener)
    }

    fun dispatch(roomCode: String, message: SignalingMessage) {
        val key = roomCode.trim().uppercase()
        channelListeners[key]?.forEach { listener ->
            try {
                listener(message)
            } catch (e: Exception) {
                Log.w("LocalSignalingHub", "Error in local listener: ${e.message}")
            }
        }
    }
}

class SignalingClient(
    private var serverBaseWsUrl: String = "ws://10.0.2.2:9090/ws"
) {
    fun getServerBaseWsUrl(): String = serverBaseWsUrl

    fun setServerBaseWsUrl(url: String) {
        serverBaseWsUrl = url
    }

    companion object {
        private const val TAG = "SignalingClient"
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 16_000L
        private const val MAX_FAILED_ATTEMPTS_BEFORE_LOCAL = 2
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var activeRoomCode: String = ""
    private var activeRole: RoomRole = RoomRole.VIEWER
    private var isIntentionalDisconnect = false
    private var isLocalMode = false
    private var reconnectAttempts = 0

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val listeners = mutableListOf<SignalingListener>()
    private val sequenceCounter = AtomicLong(1L)
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS

    private val localHubCallback: (SignalingMessage) -> Unit = { message ->
        synchronized(listeners) {
            listeners.forEach { it.onMessageReceived(message) }
        }
    }

    fun addListener(listener: SignalingListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: SignalingListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun nextSequence(): Long = sequenceCounter.getAndIncrement()

    fun connect(roomCode: String, role: RoomRole) {
        activeRoomCode = roomCode.trim().uppercase()
        activeRole = role
        isIntentionalDisconnect = false
        isLocalMode = false
        reconnectAttempts = 0
        currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS

        LocalSignalingHub.register(activeRoomCode, localHubCallback)
        initiateWebSocket()
    }

    private fun initiateWebSocket() {
        if (isLocalMode) return

        _connectionState.value = ConnectionState.CONNECTING
        val wsUrl = "$serverBaseWsUrl?roomCode=$activeRoomCode&role=${activeRole.name}"
        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened for room: $activeRoomCode")
                reconnectAttempts = 0
                isLocalMode = false
                _connectionState.value = ConnectionState.CONNECTED
                currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS
                startHeartbeat()

                synchronized(listeners) {
                    listeners.forEach { it.onConnected() }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = SignalingMessage.fromJson(text) ?: return
                synchronized(listeners) {
                    listeners.forEach { it.onMessageReceived(message) }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                handleDisconnect(reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                reconnectAttempts++
                Log.w(TAG, "WebSocket connection attempt $reconnectAttempts failed: ${t.localizedMessage}")

                if (reconnectAttempts >= MAX_FAILED_ATTEMPTS_BEFORE_LOCAL && !isLocalMode) {
                    Log.i(TAG, "Signaling server unreachable. Activating Local In-Memory Hub for room $activeRoomCode")
                    enableLocalFallback()
                } else {
                    handleDisconnect(t.localizedMessage ?: "Connection error")
                }
            }
        })
    }

    private fun enableLocalFallback() {
        isLocalMode = true
        _connectionState.value = ConnectionState.CONNECTED
        synchronized(listeners) {
            listeners.forEach { it.onConnected() }
        }
    }

    fun send(message: SignalingMessage): Boolean {
        if (isLocalMode) {
            LocalSignalingHub.dispatch(activeRoomCode, message)
            return true
        }

        val ws = webSocket
        if (ws != null && _connectionState.value == ConnectionState.CONNECTED) {
            val json = message.toJson()
            return ws.send(json)
        }
        return false
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        LocalSignalingHub.unregister(activeRoomCode, localHubCallback)
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isLocalMode = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun handleDisconnect(reason: String) {
        heartbeatJob?.cancel()
        synchronized(listeners) {
            listeners.forEach { it.onDisconnected(reason) }
        }

        if (isIntentionalDisconnect || isLocalMode) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        // Auto-reconnect with exponential backoff
        _connectionState.value = ConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(currentReconnectDelay)
            currentReconnectDelay = (currentReconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            if (!isIntentionalDisconnect && activeRoomCode.isNotEmpty()) {
                Log.d(TAG, "Attempting reconnect to room $activeRoomCode...")
                initiateWebSocket()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED && !isLocalMode) {
                delay(HEARTBEAT_INTERVAL_MS)
                val pingMsg = SignalingMessage(
                    type = SignalingMessage.TYPE_PING,
                    roomCode = activeRoomCode,
                    sequence = nextSequence()
                )
                send(pingMsg)
            }
        }
    }
}
