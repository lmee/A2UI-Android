package org.a2ui.compose.service

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Contextual
import org.a2ui.compose.data.*
import org.a2ui.compose.rendering.*
import org.a2ui.compose.transport.*
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun rememberA2UIRenderer(
    transport: A2UITransport? = null,
    actionHandler: ActionHandler? = null,
    logger: A2UILogger = DefaultLogger()
): A2UIRendererState {
    val renderer = remember {
        A2UIRenderer(logger)
    }

    LaunchedEffect(transport) {
        transport?.let { t ->
            t.messages.collectLatest { message ->
                renderer.processMessage(message)
            }
        }
    }

    LaunchedEffect(actionHandler) {
        renderer.setActionHandler(actionHandler)
    }

    return remember(renderer) { A2UIRendererState(renderer) }
}

class A2UIRendererState(val renderer: A2UIRenderer) {
    fun processMessage(message: String): Result<Unit> {
        return renderer.processMessage(message)
    }

    fun processMessages(messages: List<String>) {
        messages.forEach { processMessage(it) }
    }

    @Composable
    fun renderSurface(surfaceId: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
        val composable = renderer.renderSurface(surfaceId)
        composable()
    }

    fun getSurfaceContext(surfaceId: String): SurfaceContext? {
        return renderer.getSurfaceContext(surfaceId)
    }

    fun getAllSurfaceIds(): List<String> {
        return renderer.getAllSurfaceIds()
    }

    fun dispose() {
        renderer.dispose()
    }
}

@Composable
fun A2UISurface(
    surfaceId: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    rendererState: A2UIRendererState = LocalA2UIContext.current.rendererState
) {
    val context = rendererState.renderer.getSurfaceContext(surfaceId)
    // ✅ 直接读取，利用 SnapshotStateMap 响应式更新（不用 remember 缓存）
    val rootComponent = rendererState.renderer.getComponent(surfaceId, "root")

    if (context != null && rootComponent != null) {
        val registry = remember { ComponentRegistry(rendererState.renderer) }
        registry.render(rootComponent, context)
    } else {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = modifier
        )
    }
}

val LocalA2UIContext = compositionLocalOf<A2UIService> {
    error("A2UIContext not provided")
}

@Composable
fun A2UIProvider(
    service: A2UIService,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalA2UIContext provides service) {
        content()
    }
}

/**
 * A2UI 服务类，管理渲染器和传输层的生命周期
 *
 * 实现 AutoCloseable 接口，确保资源正确释放
 *
 * @param renderer A2UI 渲染器实例
 * @param transport 传输层实例（可选）
 */
class A2UIService(
    private val renderer: A2UIRenderer = A2UIRenderer(),
    private var transport: A2UITransport? = null
) : AutoCloseable {
    private val _isConnected = mutableStateOf(false)
    val isConnected: Boolean
        get() = _isConnected.value

    val rendererState = A2UIRendererState(renderer)

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
    )

    // ✅ AtomicBoolean 防止并发关闭竞态
    private val isClosed = AtomicBoolean(false)

    fun setTransport(t: A2UITransport?) {
        if (isClosed.get()) {
            throw IllegalStateException("Service is closed")
        }

        // ✅ 关闭旧的传输层
        if (transport is AutoCloseable && transport !== t) {
            (transport as AutoCloseable).close()
        }

        transport = t
    }

    suspend fun connect() {
        if (isClosed.get()) {
            throw IllegalStateException("Service is closed")
        }

        transport?.connect()
        transport?.messages?.collectLatest { message ->
            renderer.processMessage(message)
        }
    }

    suspend fun disconnect() {
        transport?.disconnect()
    }

    fun processMessage(message: String): Result<Unit> {
        return renderer.processMessage(message)
    }

    fun processMessages(messages: List<String>) {
        messages.forEach { processMessage(it) }
    }

    suspend fun sendAction(surfaceId: String, actionName: String, context: Map<String, Any>) {
        if (isClosed.get()) {
            throw IllegalStateException("Service is closed")
        }

        val dataModel = if (renderer.getSurfaceContext(surfaceId)?.sendDataModel == true) {
            renderer.getDataModel(surfaceId)?.getDataSnapshot()
        } else null

        transport?.send(kotlinx.serialization.json.Json.encodeToString(
            ActionMessage.serializer(),
            ActionMessage(
                surfaceId = surfaceId,
                actionName = actionName,
                context = context,
                dataModel = dataModel
            )
        ))
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
     * 关闭服务并释放所有资源
     *
     * 此方法是幂等的，可以安全地多次调用
     */
    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return

        // 1. 取消所有协程任务
        scope.cancel()

        // 2. 关闭传输层
        if (transport is AutoCloseable) {
            (transport as AutoCloseable).close()
        }
        transport = null

        // 3. 清理渲染器
        renderer.dispose()
    }
}

@kotlinx.serialization.Serializable
data class ActionMessage(
    val surfaceId: String,
    val actionName: String,
    val context: Map<String, @Contextual Any>,
    val dataModel: Map<String, @Contextual Any?>? = null
)
