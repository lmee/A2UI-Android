package org.a2ui.compose.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
import org.a2ui.compose.rendering.*
import org.a2ui.compose.service.A2UIService
import org.a2ui.compose.transport.WebSocketTransport

/**
 * 展示正确的资源管理模式的示例 Activity
 *
 * 关键点：
 * 1. 使用 AutoCloseable 接口
 * 2. 在 onDestroy 中正确释放资源
 * 3. 使用 use 块模式（可选）
 */
class A2UIResourceManagementExample : ComponentActivity() {

    // ✅ 保持对服务的引用，以便在 onDestroy 中清理
    private var service: A2UIService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 方式 1: 手动管理资源
        val renderer = A2UIRenderer()
        val transport = WebSocketTransport("ws://example.com/a2ui")
        service = A2UIService(renderer, transport)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ResourceManagedScreen(service!!)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // ✅ 确保资源释放
        service?.close()
        service = null
    }
}

@Composable
fun ResourceManagedScreen(service: A2UIService) {
    val scope = rememberCoroutineScope()
    var isConnected by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "A2UI Resource Management Example",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    try {
                        service.connect()
                        isConnected = true
                    } catch (e: Exception) {
                        // 处理连接错误
                    }
                }
            },
            enabled = !isConnected
        ) {
            Text("Connect")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    service.disconnect()
                    isConnected = false
                }
            },
            enabled = isConnected
        ) {
            Text("Disconnect")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Status: ${if (isConnected) "Connected" else "Disconnected"}",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * 展示 use 块模式的示例函数
 */
fun processMessagesWithAutoCleanup(url: String, messages: List<String>) {
    // ✅ 方式 2: 使用 use 块自动清理
    WebSocketTransport(url).use { transport ->
        val renderer = A2UIRenderer()
        A2UIService(renderer, transport).use { service ->
            // 处理消息
            messages.forEach { message ->
                service.processMessage(message)
            }

            // 渲染 UI
            val surfaceIds = service.rendererState.getAllSurfaceIds()
            println("Created surfaces: $surfaceIds")
        } // ✅ 自动调用 service.close()
    } // ✅ 自动调用 transport.close()
}

/**
 * 展示嵌套资源管理的示例
 */
class NestedResourceExample {
    fun processWithMultipleTransports() {
        // ✅ 嵌套 use 块
        WebSocketTransport("ws://server1.com").use { transport1 ->
            WebSocketTransport("ws://server2.com").use { transport2 ->
                val renderer = A2UIRenderer()

                A2UIService(renderer, transport1).use { service1 ->
                    // 使用 service1
                    service1.processMessage("{}")

                    // 切换到另一个传输层
                    service1.setTransport(transport2)

                    // 使用 service1 with transport2
                    service1.processMessage("{}")
                }
            }
        }
    }
}

/**
 * 展示错误处理的示例
 */
class ErrorHandlingExample {
    fun processWithErrorHandling(url: String) {
        var transport: WebSocketTransport? = null
        var service: A2UIService? = null

        try {
            transport = WebSocketTransport(url)
            val renderer = A2UIRenderer()
            service = A2UIService(renderer, transport)

            // 处理消息
            service.processMessage("{}")

        } catch (e: Exception) {
            // 处理错误
            println("Error: ${e.message}")

        } finally {
            // ✅ 确保资源释放
            service?.close()
            transport?.close()
        }
    }
}

/**
 * 展示协程作用域管理的示例
 */
@Composable
fun CoroutineScopeExample() {
    val scope = rememberCoroutineScope()
    var service by remember { mutableStateOf<A2UIService?>(null) }

    DisposableEffect(Unit) {
        // 创建服务
        val transport = WebSocketTransport("ws://example.com")
        val renderer = A2UIRenderer()
        service = A2UIService(renderer, transport)

        // 启动连接
        scope.launch {
            service?.connect()
        }

        onDispose {
            // ✅ Composable 销毁时自动清理
            service?.close()
            service = null
        }
    }

    // UI 代码...
}

/**
 * 最佳实践总结
 */
object ResourceManagementBestPractices {
    /**
     * ✅ 推荐做法：
     *
     * 1. 在 Activity/Fragment 的 onDestroy 中调用 close()
     * 2. 使用 DisposableEffect 在 Composable 销毁时清理
     * 3. 使用 use 块自动管理资源
     * 4. 保持对资源的引用，以便清理
     * 5. 处理异常时也要确保资源释放（finally 块）
     */

    /**
     * ❌ 错误做法：
     *
     * 1. 忘记调用 close() 或 dispose()
     * 2. 创建资源后不保存引用
     * 3. 在异常情况下不清理资源
     * 4. 重复创建资源而不清理旧的
     * 5. 在后台线程中创建资源但不管理生命周期
     */

    fun goodExample() {
        // ✅ 好的例子
        val service = A2UIService()
        try {
            service.processMessage("{}")
        } finally {
            service.close()
        }
    }

    fun badExample() {
        // ❌ 坏的例子
        val service = A2UIService()
        service.processMessage("{}")
        // 忘记调用 close()，导致内存泄漏
    }
}

/**
 * ✅ LifecycleObserver 模式示例（替代 finalize 反模式）
 *
 * 通过 LifecycleObserver 自动管理 A2UIService 生命周期，
 * 无需依赖 finalize() 进行资源清理。
 */
class A2UILifecycleObserver(
    private val url: String,
    private val logger: A2UILogger = DefaultLogger()
) : DefaultLifecycleObserver {

    private var service: A2UIService? = null
    private var transport: WebSocketTransport? = null

    val currentService: A2UIService?
        get() = service

    override fun onCreate(owner: LifecycleOwner) {
        val renderer = A2UIRenderer(logger)
        transport = WebSocketTransport(url)
        service = A2UIService(renderer, transport)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // ✅ 自动清理，无需 finalize()
        service?.close()
        service = null
        transport = null
        owner.lifecycle.removeObserver(this)
    }
}

/**
 * 使用示例：
 *
 * ```kotlin
 * class MyActivity : ComponentActivity() {
 *     private val a2uiObserver = A2UILifecycleObserver("ws://example.com/a2ui")
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         lifecycle.addObserver(a2uiObserver)
 *
 *         setContent {
 *             a2uiObserver.currentService?.let { service ->
 *                 // 使用 service 渲染 UI
 *             }
 *         }
 *     }
 *     // 无需手动 onDestroy 清理 — LifecycleObserver 自动处理
 * }
 * ```
 */
