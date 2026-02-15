package org.a2ui.compose.rendering

import org.junit.Test
import org.junit.Assert.*
import org.a2ui.compose.data.*

class A2UIRendererTest {

    private val renderer = A2UIRenderer()

    @Test
    fun testProcessCreateSurface() {
        val message = """{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "test_surface",
                "catalogId": "https://example.com/catalog.json"
            }
        }"""

        val result = renderer.processMessage(message)
        assertTrue(result.isSuccess)

        val context = renderer.getSurfaceContext("test_surface")
        assertNotNull(context)
        assertEquals("test_surface", context?.surfaceId)
        assertEquals("https://example.com/catalog.json", context?.catalogId)
    }

    @Test
    fun testProcessCreateSurface_withTheme() {
        val message = """{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "themed_surface",
                "catalogId": "https://example.com/catalog.json",
                "theme": {
                    "primaryColor": "#6200EE"
                }
            }
        }"""

        val result = renderer.processMessage(message)
        assertTrue(result.isSuccess)

        val context = renderer.getSurfaceContext("themed_surface")
        assertNotNull(context)
        assertEquals("#6200EE", context?.theme?.primaryColor)
    }

    @Test
    fun testProcessUpdateComponents() {
        renderer.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "test_surface",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        val message = """{
            "version": "v0.10",
            "updateComponents": {
                "surfaceId": "test_surface",
                "components": [
                    {
                        "id": "root",
                        "component": "Text",
                        "text": "Hello World"
                    }
                ]
            }
        }"""

        val result = renderer.processMessage(message)
        assertTrue(result.isSuccess)

        val component = renderer.getComponent("test_surface", "root")
        assertNotNull(component)
        assertEquals("Text", component?.component)
    }

    @Test
    fun testProcessUpdateComponents_multipleComponents() {
        renderer.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "multi_surface",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        val message = """{
            "version": "v0.10",
            "updateComponents": {
                "surfaceId": "multi_surface",
                "components": [
                    {"id": "root", "component": "Column", "children": {"array": ["text1", "text2"]}},
                    {"id": "text1", "component": "Text", "text": "First"},
                    {"id": "text2", "component": "Text", "text": "Second"}
                ]
            }
        }"""

        val result = renderer.processMessage(message)
        assertTrue(result.isSuccess)

        assertNotNull(renderer.getComponent("multi_surface", "root"))
        assertNotNull(renderer.getComponent("multi_surface", "text1"))
        assertNotNull(renderer.getComponent("multi_surface", "text2"))
    }

    @Test
    fun testProcessUpdateDataModel() {
        renderer.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "data_surface",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        val message = """{
            "version": "v0.10",
            "updateDataModel": {
                "surfaceId": "data_surface",
                "path": "/user/name",
                "value": "John Doe"
            }
        }"""

        val result = renderer.processMessage(message)
        assertTrue(result.isSuccess)

        val dataModel = renderer.getDataModel("data_surface")
        assertNotNull(dataModel)
        assertEquals("John Doe", dataModel?.getValue("/user/name"))
    }

    @Test
    fun testProcessUpdateDataModel_complexObject() {
        renderer.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "complex_surface",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        val message = """{
            "version": "v0.10",
            "updateDataModel": {
                "surfaceId": "complex_surface",
                "path": "/",
                "value": {
                    "user": {
                        "name": "John",
                        "email": "john@example.com",
                        "address": {
                            "city": "New York",
                            "country": "USA"
                        }
                    },
                    "items": ["item1", "item2", "item3"]
                }
            }
        }"""

        val result = renderer.processMessage(message)
        assertTrue(result.isSuccess)

        val dataModel = renderer.getDataModel("complex_surface")
        assertNotNull(dataModel)
        assertEquals("John", dataModel?.getValue("/user/name"))
        assertEquals("john@example.com", dataModel?.getValue("/user/email"))
        assertEquals("New York", dataModel?.getValue("/user/address/city"))
    }

    @Test
    fun testProcessDeleteSurface() {
        renderer.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "delete_surface",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        assertNotNull(renderer.getSurfaceContext("delete_surface"))

        val message = """{
            "version": "v0.10",
            "deleteSurface": {
                "surfaceId": "delete_surface"
            }
        }"""

        renderer.processMessage(message)

        assertNull(renderer.getSurfaceContext("delete_surface"))
        assertNull(renderer.getDataModel("delete_surface"))
    }

    @Test
    fun testProcessInvalidMessage() {
        val message = """{
            "version": "v0.10",
            "invalidMessage": {}
        }"""

        val result = renderer.processMessage(message)
        assertTrue(result.isFailure)
    }

    @Test
    fun testProcessEmptyMessage() {
        val result = renderer.processMessage("")
        assertTrue(result.isFailure)
    }

    @Test
    fun testProcessMalformedJson() {
        val result = renderer.processMessage("{ invalid json }")
        assertTrue(result.isFailure)
    }

    @Test
    fun testGetAllSurfaceIds() {
        renderer.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "surface1",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        renderer.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "surface2",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        val surfaceIds = renderer.getAllSurfaceIds()
        assertEquals(2, surfaceIds.size)
        assertTrue(surfaceIds.contains("surface1"))
        assertTrue(surfaceIds.contains("surface2"))
    }

    @Test
    fun testSaveAndRestoreState() {
        val renderer1 = A2UIRenderer()
        
        renderer1.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "save_surface",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        renderer1.processMessage("""{
            "version": "v0.10",
            "updateDataModel": {
                "surfaceId": "save_surface",
                "path": "/data",
                "value": "test_value"
            }
        }""")

        val savedState = renderer1.saveState()

        val renderer2 = A2UIRenderer()
        renderer2.restoreState(savedState)

        assertNotNull(renderer2.getSurfaceContext("save_surface"))
        assertEquals("test_value", renderer2.getDataModel("save_surface")?.getValue("/data"))
    }

    @Test
    fun testDispose() {
        renderer.processMessage("""{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "dispose_surface",
                "catalogId": "https://example.com/catalog.json"
            }
        }""")

        assertNotNull(renderer.getSurfaceContext("dispose_surface"))

        renderer.dispose()

        assertTrue(renderer.getAllSurfaceIds().isEmpty())
    }

    @Test
    fun testUpdateNonExistentSurface() {
        val result = renderer.processMessage("""{
            "version": "v0.10",
            "updateComponents": {
                "surfaceId": "nonexistent_surface",
                "components": [
                    {"id": "root", "component": "Text", "text": "Test"}
                ]
            }
        }""")

        // Should succeed but not create the component
        assertTrue(result.isSuccess)
        assertNull(renderer.getComponent("nonexistent_surface", "root"))
    }
}
