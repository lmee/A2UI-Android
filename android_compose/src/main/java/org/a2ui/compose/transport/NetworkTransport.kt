package org.a2ui.compose.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class WebSocketTransport(
    private val url: String,
    private val reconnectEnabled: Boolean = true,
    private val reconnectDelayMs: Long = 3000
) : A2UITransport {

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: Flow<TransportState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(replay = 0)
    override val messages: Flow<String> = _messages.asSharedFlow()

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    override suspend fun connect() {
        _state.value = TransportState.Connecting

        try {
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    scope.launch {
                        _state.value = TransportState.Connected
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch {
                        _messages.emit(text)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scope.launch {
                        _state.value = TransportState.Disconnected
                        if (reconnectEnabled) {
                            scheduleReconnect()
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch {
                        _state.value = TransportState.Error(t.message ?: "Connection failed")
                        if (reconnectEnabled) {
                            scheduleReconnect()
                        }
                    }
                }
            })

            _state.value = TransportState.Connected
        } catch (e: Exception) {
            _state.value = TransportState.Error(e.message ?: "Connection failed")
            if (reconnectEnabled) {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelayMs)
            connect()
        }
    }

    override suspend fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _state.value = TransportState.Disconnected
    }

    override suspend fun send(message: String) {
        if (_state.value != TransportState.Connected) {
            throw IllegalStateException("WebSocket is not connected")
        }
        webSocket?.send(message)
    }

    fun dispose() {
        reconnectJob?.cancel()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}

class SSETransport(
    private val url: String,
    private val reconnectEnabled: Boolean = true,
    private val reconnectDelayMs: Long = 3000
) : A2UITransport {

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: Flow<TransportState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(replay = 0)
    override val messages: Flow<String> = _messages.asSharedFlow()

    private var eventSource: EventSource? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    override suspend fun connect() {
        _state.value = TransportState.Connecting
        connectSSE()
    }

    private fun connectSSE() {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        val factory = EventSources.createFactory(client)
        
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                scope.launch {
                    _state.value = TransportState.Connected
                }
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                scope.launch {
                    if (data.isNotEmpty() && data != "[DONE]") {
                        _messages.emit(data)
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                scope.launch {
                    _state.value = TransportState.Disconnected
                    if (reconnectEnabled) {
                        scheduleReconnect()
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                scope.launch {
                    _state.value = TransportState.Error(t?.message ?: "Connection failed")
                    if (reconnectEnabled) {
                        scheduleReconnect()
                    }
                }
            }
        })

        _state.value = TransportState.Connected
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelayMs)
            connectSSE()
        }
    }

    override suspend fun disconnect() {
        reconnectJob?.cancel()
        eventSource?.cancel()
        eventSource = null
        _state.value = TransportState.Disconnected
    }

    override suspend fun send(message: String) {
        throw UnsupportedOperationException("SSE is a read-only transport. Use a separate transport for sending messages.")
    }

    fun dispose() {
        reconnectJob?.cancel()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}
