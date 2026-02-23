package org.a2ui.compose.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * WebSocket 传输层实现，支持双向通信
 *
 * 特性：
 * - 自动重连机制（可配置）
 * - 心跳检测（30 秒间隔）
 * - 资源自动清理（实现 AutoCloseable）
 * - 防止内存泄漏
 *
 * @param url WebSocket 服务器地址
 * @param reconnectEnabled 是否启用自动重连
 * @param reconnectDelayMs 重连延迟（毫秒）
 */
class WebSocketTransport(
    private val url: String,
    private val reconnectEnabled: Boolean = true,
    private val reconnectDelayMs: Long = 3000
) : A2UITransport, AutoCloseable {

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

    // ✅ 添加状态标志，防止关闭后继续使用
    @Volatile
    private var isClosed = false

    override suspend fun connect() {
        // ✅ 检查是否已关闭
        if (isClosed) {
            throw IllegalStateException("Transport is closed and cannot be reconnected")
        }

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
        // ✅ 检查是否已关闭
        if (isClosed) {
            throw IllegalStateException("Transport is closed")
        }

        if (_state.value != TransportState.Connected) {
            throw IllegalStateException("WebSocket is not connected")
        }
        webSocket?.send(message)
    }

    /**
     * 释放所有资源（已废弃，请使用 close()）
     */
    @Deprecated(
        message = "Use close() instead",
        replaceWith = ReplaceWith("close()"),
        level = DeprecationLevel.WARNING
    )
    fun dispose() {
        close()
    }

    /**
     * 关闭传输层并释放所有资源
     *
     * 此方法是幂等的，可以安全地多次调用
     */
    override fun close() {
        if (isClosed) return

        isClosed = true

        // 1. 取消所有协程任务
        reconnectJob?.cancel()
        scope.cancel()

        // 2. 关闭 WebSocket 连接
        webSocket?.close(1000, "Transport closed")
        webSocket = null

        // 3. 关闭 OkHttp 客户端资源
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        } catch (e: Exception) {
            // 忽略关闭时的异常
        }

        // 4. 更新状态
        _state.value = TransportState.Disconnected
    }

    /**
     * 析构函数保护，防止忘记调用 close()
     */
    @Suppress("DEPRECATION")
    protected fun finalize() {
        if (!isClosed) {
            close()
        }
    }
}

/**
 * Server-Sent Events (SSE) 传输层实现，支持单向接收
 *
 * 特性：
 * - 只读传输（不支持发送消息）
 * - 自动重连机制（可配置）
 * - 资源自动清理（实现 AutoCloseable）
 * - 防止内存泄漏
 *
 * @param url SSE 服务器地址
 * @param reconnectEnabled 是否启用自动重连
 * @param reconnectDelayMs 重连延迟（毫秒）
 */
class SSETransport(
    private val url: String,
    private val reconnectEnabled: Boolean = true,
    private val reconnectDelayMs: Long = 3000
) : A2UITransport, AutoCloseable {

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

    // ✅ 添加状态标志，防止关闭后继续使用
    @Volatile
    private var isClosed = false

    override suspend fun connect() {
        // ✅ 检查是否已关闭
        if (isClosed) {
            throw IllegalStateException("Transport is closed and cannot be reconnected")
        }

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

    /**
     * 释放所有资源（已废弃，请使用 close()）
     */
    @Deprecated(
        message = "Use close() instead",
        replaceWith = ReplaceWith("close()"),
        level = DeprecationLevel.WARNING
    )
    fun dispose() {
        close()
    }

    /**
     * 关闭传输层并释放所有资源
     *
     * 此方法是幂等的，可以安全地多次调用
     */
    override fun close() {
        if (isClosed) return

        isClosed = true

        // 1. 取消所有协程任务
        reconnectJob?.cancel()
        scope.cancel()

        // 2. 关闭 EventSource 连接
        eventSource?.cancel()
        eventSource = null

        // 3. 关闭 OkHttp 客户端资源
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        } catch (e: Exception) {
            // 忽略关闭时的异常
        }

        // 4. 更新状态
        _state.value = TransportState.Disconnected
    }

    /**
     * 析构函数保护，防止忘记调用 close()
     */
    @Suppress("DEPRECATION")
    protected fun finalize() {
        if (!isClosed) {
            close()
        }
    }
}
