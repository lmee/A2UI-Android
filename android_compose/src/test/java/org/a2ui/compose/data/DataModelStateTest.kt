package org.a2ui.compose.data

import org.junit.Test
import org.junit.Assert.*

class DataModelStateTest {

    @Test
    fun testUpdateDataModel_simplePath() {
        val state = DataModelState()
        state.updateDataModel("/name", "John")
        assertEquals("John", state.getValue("/name"))
    }

    @Test
    fun testUpdateDataModel_nestedPath() {
        val state = DataModelState()
        state.updateDataModel("/user/name", "John")
        assertEquals("John", state.getValue("/user/name"))
    }

    @Test
    fun testUpdateDataModel_deepNestedPath() {
        val state = DataModelState()
        state.updateDataModel("/a/b/c/d/e", "value")
        assertEquals("value", state.getValue("/a/b/c/d/e"))
    }

    @Test
    fun testUpdateDataModel_replaceEntireData() {
        val state = DataModelState()
        state.updateDataModel("/", mapOf("name" to "John", "age" to 30))
        assertEquals("John", state.getValue("/name"))
        assertEquals(30, state.getValue("/age"))
    }

    @Test
    fun testUpdateDataModel_removeValue() {
        val state = DataModelState()
        state.updateDataModel("/name", "John")
        assertEquals("John", state.getValue("/name"))
        state.updateDataModel("/name", null)
        assertNull(state.getValue("/name"))
    }

    @Test
    fun testGetValue_root() {
        val state = DataModelState()
        state.updateDataModel("/name", "John")
        state.updateDataModel("/age", 30)

        val root = state.getValue("/")
        assertTrue(root is Map<*, *>)
        val rootMap = root as Map<*, *>
        assertEquals("John", rootMap["name"])
        assertEquals(30, rootMap["age"])
    }

    @Test
    fun testGetValue_nonExistentPath() {
        val state = DataModelState()
        assertNull(state.getValue("/nonexistent"))
    }

    @Test
    fun testClear() {
        val state = DataModelState()
        state.updateDataModel("/name", "John")
        state.updateDataModel("/age", 30)
        state.clear()
        assertNull(state.getValue("/name"))
        assertNull(state.getValue("/age"))
    }

    @Test
    fun testGetDataSnapshot() {
        val state = DataModelState()
        state.updateDataModel("/name", "John")
        state.updateDataModel("/age", 30)

        val snapshot = state.getDataSnapshot()
        assertEquals("John", snapshot["name"])
        assertEquals(30, snapshot["age"])
    }
}
