package org.a2ui.compose.service

import kotlinx.coroutines.runBlocking
import org.a2ui.compose.rendering.A2UIRenderer
import org.a2ui.compose.transport.WebSocketTransport
import org.junit.Assert.*
import org.junit.Test

class A2UIServiceMemoryLeakTest {

    @Test
    fun `A2UIService close releases all resources`() {
        val renderer = A2UIRenderer()
        val transport = WebSocketTransport("ws://example.com")
        val service = A2UIService(renderer, transport)

        // 关闭服务
        service.close()

        // 验证无法再使用
        try {
            service.processMessage("{}")
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `A2UIService multiple close calls are safe`() {
        val service = A2UIService()

        // 多次调用 close 不应该抛出异常
        service.close()
        service.close()
        service.close()
    }

    @Test
    fun `A2UIService dispose is deprecated but works`() {
        val service = A2UIService()

        @Suppress("DEPRECATION")
        service.dispose()

        // 验证已关闭
        try {
            service.processMessage("{}")
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `A2UIService setTransport closes old transport`() {
        val transport1 = WebSocketTransport("ws://example1.com")
        val transport2 = WebSocketTransport("ws://example2.com")
        val service = A2UIService(transport = transport1)

        // 设置新的传输层
        service.setTransport(transport2)

        // 验证旧的传输层已关闭
        try {
            runBlocking { transport1.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }

        service.close()
    }

    @Test
    fun `A2UIService setTransport throws exception after close`() {
        val service = A2UIService()
        service.close()

        // 验证无法设置传输层
        try {
            service.setTransport(WebSocketTransport("ws://example.com"))
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `A2UIService connect throws exception after close`() {
        val service = A2UIService()
        service.close()

        // 验证无法连接
        try {
            runBlocking { service.connect() }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `A2UIService sendAction throws exception after close`() {
        val service = A2UIService()
        service.close()

        // 验证无法发送动作
        try {
            runBlocking { service.sendAction("surface1", "action1", emptyMap()) }
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `A2UIService use block pattern works correctly`() {
        var closeCalled = false

        val service = A2UIService()
        try {
            // 使用 service
            service.processMessage("""
                {
                    "version": "v0.10",
                    "createSurface": {
                        "surfaceId": "test",
                        "catalogId": "test"
                    }
                }
            """.trimIndent())
        } finally {
            service.close()
            closeCalled = true
        }

        assertTrue(closeCalled)

        // 验证已关闭
        try {
            service.processMessage("{}")
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("closed") == true)
        }
    }
}
