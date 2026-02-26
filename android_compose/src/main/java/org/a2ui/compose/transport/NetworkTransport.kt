package org.a2ui.compose.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket 传输层实现，支持双向通信
 *
 * 特性：
 * - 自动重连机制（可配置）
 * - 指数退避重连策略
 * - 心跳检测（30 秒间隔）
 * - 资源自动清理（实现 AutoCloseable）
 *
 * @param url WebSocket 服务器地址
 * @param reconnectEnabled 是否启用自动重连
 * @param reconnectDelayMs 初始重连延迟（毫秒）
 * @param maxReconnectDelayMs 最大重连延迟（毫秒）
 * @param maxRetryCount 最大重试次数（0 = 无限）
 * @param client 可选的 OkHttpClient 实例，默认使用共享实例
 */
class WebSocketTransport(
    private val url: String,
    private val reconnectEnabled: Boolean = true,
    private val reconnectDelayMs: Long = 3000,
    private val maxReconnectDelayMs: Long = 60_000,
    private val maxRetryCount: Int = 0,
    private val client: OkHttpClient? = null
) : A2UITransport, AutoCloseable {

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: Flow<TransportState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(replay = 1)
    override val messages: Flow<String> = _messages.asSharedFlow()

    private var webSocket: WebSocket? = null
    private val effectiveClient: OkHttpClient = client ?: A2UIHttpClientFactory.sharedClient
    private val ownsClient: Boolean = client == null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var retryCount = 0

    private val isClosed = AtomicBoolean(false)

    override suspend fun connect() {
        if (isClosed.get()) {
            throw IllegalStateException("Transport is closed and cannot be reconnected")
        }

        _state.value = TransportState.Connecting

        try {
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = effectiveClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    scope.launch {
                        retryCount = 0
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
                        if (reconnectEnabled && !isClosed.get()) {
                            scheduleReconnect()
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch {
                        _state.value = TransportState.Error(t.message ?: "Connection failed")
                        if (reconnectEnabled && !isClosed.get()) {
                            scheduleReconnect()
                        }
                    }
                }
            })
            // ✅ 不在此处设置 Connected — 由 onOpen 回调设置
        } catch (e: Exception) {
            _state.value = TransportState.Error(e.message ?: "Connection failed")
            if (reconnectEnabled && !isClosed.get()) {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (isClosed.get()) return
        if (maxRetryCount > 0 && retryCount >= maxRetryCount) {
            _state.value = TransportState.Error("Max retry count ($maxRetryCount) reached")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            // ✅ 指数退避
            val delay = (reconnectDelayMs * (1L shl retryCount.coerceAtMost(10)))
                .coerceAtMost(maxReconnectDelayMs)
            retryCount++
            delay(delay)
            if (!isClosed.get()) {
                connect()
            }
        }
    }

    override suspend fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _state.value = TransportState.Disconnected
    }

    override suspend fun send(message: String) {
        if (isClosed.get()) {
            throw IllegalStateException("Transport is closed")
        }
        if (_state.value != TransportState.Connected) {
            throw IllegalStateException("WebSocket is not connected")
        }
        webSocket?.send(message)
    }

    @Deprecated(
        message = "Use close() instead",
        replaceWith = ReplaceWith("close()"),
        level = DeprecationLevel.WARNING
    )
    fun dispose() {
        close()
    }

    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return

        // 1. 更新状态
        _state.value = TransportState.Disconnected

        // 2. 取消所有协程任务
        reconnectJob?.cancel()
        scope.cancel()

        // 3. 关闭 WebSocket 连接
        webSocket?.close(1000, "Transport closed")
        webSocket = null

        // 4. 仅在未使用共享 client 时关闭
        if (!ownsClient) {
            try {
                effectiveClient.dispatcher.executorService.shutdown()
                effectiveClient.connectionPool.evictAll()
            } catch (_: Exception) {}
        }
    }
}

/**
 * Server-Sent Events (SSE) 传输层实现，支持单向接收
 *
 * 特性：
 * - 只读传输（不支持发送消息）
 * - 自动重连机制（指数退避）
 * - 资源自动清理（实现 AutoCloseable）
 *
 * @param url SSE 服务器地址
 * @param reconnectEnabled 是否启用自动重连
 * @param reconnectDelayMs 初始重连延迟（毫秒）
 * @param maxReconnectDelayMs 最大重连延迟（毫秒）
 * @param maxRetryCount 最大重试次数（0 = 无限）
 * @param client 可选的 OkHttpClient 实例，默认使用共享实例
 */
class SSETransport(
    private val url: String,
    private val reconnectEnabled: Boolean = true,
    private val reconnectDelayMs: Long = 3000,
    private val maxReconnectDelayMs: Long = 60_000,
    private val maxRetryCount: Int = 0,
    private val client: OkHttpClient? = null
) : A2UITransport, AutoCloseable {

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: Flow<TransportState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(replay = 1)
    override val messages: Flow<String> = _messages.asSharedFlow()

    private var eventSource: EventSource? = null
    private val effectiveClient: OkHttpClient = client ?: A2UIHttpClientFactory.sharedClient
    private val ownsClient: Boolean = client == null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var retryCount = 0

    private val isClosed = AtomicBoolean(false)

    override suspend fun connect() {
        if (isClosed.get()) {
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

        val factory = EventSources.createFactory(effectiveClient)

        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                scope.launch {
                    retryCount = 0
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
                    if (reconnectEnabled && !isClosed.get()) {
                        scheduleReconnect()
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                scope.launch {
                    _state.value = TransportState.Error(t?.message ?: "Connection failed")
                    if (reconnectEnabled && !isClosed.get()) {
                        scheduleReconnect()
                    }
                }
            }
        })
        // ✅ 不在此处设置 Connected — 由 onOpen 回调设置
    }

    private fun scheduleReconnect() {
        if (isClosed.get()) return
        if (maxRetryCount > 0 && retryCount >= maxRetryCount) {
            _state.value = TransportState.Error("Max retry count ($maxRetryCount) reached")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delay = (reconnectDelayMs * (1L shl retryCount.coerceAtMost(10)))
                .coerceAtMost(maxReconnectDelayMs)
            retryCount++
            delay(delay)
            if (!isClosed.get()) {
                connectSSE()
            }
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

    @Deprecated(
        message = "Use close() instead",
        replaceWith = ReplaceWith("close()"),
        level = DeprecationLevel.WARNING
    )
    fun dispose() {
        close()
    }

    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return

        // 1. 更新状态
        _state.value = TransportState.Disconnected

        // 2. 取消所有协程任务
        reconnectJob?.cancel()
        scope.cancel()

        // 3. 关闭 EventSource 连接
        eventSource?.cancel()
        eventSource = null

        // 4. 仅在未使用共享 client 时关闭
        if (!ownsClient) {
            try {
                effectiveClient.dispatcher.executorService.shutdown()
                effectiveClient.connectionPool.evictAll()
            } catch (_: Exception) {}
        }
    }
}
