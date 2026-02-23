package org.a2ui.compose.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NetworkTransportMemoryLeakTest {

    @Test
    fun `WebSocketTransport close cancels all coroutines`() = runTest {
        val transport = WebSocketTransport("ws://example.com")

        // 尝试连接（会失败，但会启动协程）
        launch {
            try {
                transport.connect()
            } catch (e: Exception) {
                // 预期会失败
            }
        }

        delay(100)

        // 关闭传输层
        transport.close()

        // 验证无法再使用
        try {
            runBlocking { transport.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `SSETransport close cancels all coroutines`() = runTest {
        val transport = SSETransport("http://example.com/events")

        // 尝试连接（会失败，但会启动协程）
        launch {
            try {
                transport.connect()
            } catch (e: Exception) {
                // 预期会失败
            }
        }

        delay(100)

        // 关闭传输层
        transport.close()

        // 验证无法再使用
        try {
            runBlocking { transport.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `WebSocketTransport use block automatically closes transport`() {
        var closeCalled = false

        // 模拟 use 块
        val transport = WebSocketTransport("ws://example.com")
        try {
            // 使用 transport
        } finally {
            transport.close()
            closeCalled = true
        }

        assertTrue(closeCalled)

        // 验证已关闭
        try {
            runBlocking { transport.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `SSETransport use block automatically closes transport`() {
        var closeCalled = false

        // 模拟 use 块
        val transport = SSETransport("http://example.com/events")
        try {
            // 使用 transport
        } finally {
            transport.close()
            closeCalled = true
        }

        assertTrue(closeCalled)

        // 验证已关闭
        try {
            runBlocking { transport.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `WebSocketTransport multiple close calls are safe`() {
        val transport = WebSocketTransport("ws://example.com")

        // 多次调用 close 不应该抛出异常
        transport.close()
        transport.close()
        transport.close()

        // 验证已关闭
        try {
            runBlocking { transport.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `SSETransport multiple close calls are safe`() {
        val transport = SSETransport("http://example.com/events")

        // 多次调用 close 不应该抛出异常
        transport.close()
        transport.close()
        transport.close()

        // 验证已关闭
        try {
            runBlocking { transport.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `WebSocketTransport send throws exception after close`() {
        val transport = WebSocketTransport("ws://example.com")

        transport.close()

        // 验证无法发送消息
        try {
            runBlocking { transport.send("test") }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `SSETransport send always throws UnsupportedOperationException`() {
        val transport = SSETransport("http://example.com/events")

        // SSE 不支持发送消息
        try {
            runBlocking { transport.send("test") }
            fail("Should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("read-only") == true)
        }

        transport.close()
    }

    @Test
    fun `WebSocketTransport dispose is deprecated but works`() {
        val transport = WebSocketTransport("ws://example.com")

        @Suppress("DEPRECATION")
        transport.dispose()

        // 验证已关闭
        try {
            runBlocking { transport.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `SSETransport dispose is deprecated but works`() {
        val transport = SSETransport("http://example.com/events")

        @Suppress("DEPRECATION")
        transport.dispose()

        // 验证已关闭
        try {
            runBlocking { transport.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `WebSocketTransport state is Disconnected after close`() = runTest {
        val transport = WebSocketTransport("ws://example.com")

        transport.close()

        // 验证状态
        assertEquals(TransportState.Disconnected, transport.state.value)
    }

    @Test
    fun `SSETransport state is Disconnected after close`() = runTest {
        val transport = SSETransport("http://example.com/events")

        transport.close()

        // 验证状态
        assertEquals(TransportState.Disconnected, transport.state.value)
    }
}
