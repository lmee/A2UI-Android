package org.a2ui.compose.data

import org.junit.Test
import org.junit.Assert.*

class DataModelProcessorTest {

    private val processor = DataModelProcessor()

    @Test
    fun testCreateSurface() {
        processor.createSurface("test_surface")
        assertNotNull(processor.getDataModel("test_surface"))
    }

    @Test
    fun testDeleteSurface() {
        processor.createSurface("test_surface")
        processor.deleteSurface("test_surface")
        assertNull(processor.getDataModel("test_surface"))
    }

    @Test
    fun testUpdateDataModel() {
        processor.createSurface("test_surface")
        processor.updateDataModel("test_surface", "/name", "John")
        assertEquals("John", processor.getValue("test_surface", "/name"))
    }

    @Test
    fun testResolveDynamicValue_literal() {
        processor.createSurface("test_surface")
        val literal = DynamicValue.LiteralValue("Hello")
        assertEquals("Hello", processor.resolveDynamicValue("test_surface", literal))
    }

    @Test
    fun testResolveDynamicValue_path() {
        processor.createSurface("test_surface")
        processor.updateDataModel("test_surface", "/user/name", "John")
        
        val pathValue = DynamicValue.PathValue<String>("/user/name")
        assertEquals("John", processor.resolveDynamicValue("test_surface", pathValue))
    }

    @Test
    fun testResolveFunctionCall_required() {
        val functionCall = FunctionCall("required", mapOf("value" to ""))
        assertFalse(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(functionCall)) as Boolean)

        val functionCall2 = FunctionCall("required", mapOf("value" to "test"))
        assertTrue(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(functionCall2)) as Boolean)
    }

    @Test
    fun testResolveFunctionCall_email() {
        val validEmail = FunctionCall("email", mapOf("value" to "test@example.com"))
        assertTrue(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(validEmail)) as Boolean)

        val invalidEmail = FunctionCall("email", mapOf("value" to "invalid-email"))
        assertFalse(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(invalidEmail)) as Boolean)
    }

    @Test
    fun testResolveFunctionCall_url() {
        val validUrl = FunctionCall("url", mapOf("value" to "https://example.com"))
        assertTrue(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(validUrl)) as Boolean)

        val invalidUrl = FunctionCall("url", mapOf("value" to "not-a-url"))
        assertFalse(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(invalidUrl)) as Boolean)
    }

    @Test
    fun testResolveFunctionCall_phone() {
        val validPhone = FunctionCall("phone", mapOf("value" to "1234567890"))
        assertTrue(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(validPhone)) as Boolean)

        val invalidPhone = FunctionCall("phone", mapOf("value" to "123"))
        assertFalse(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(invalidPhone)) as Boolean)
    }

    @Test
    fun testResolveFunctionCall_length() {
        val validLength = FunctionCall("length", mapOf("value" to "hello", "min" to 1, "max" to 10))
        assertTrue(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(validLength)) as Boolean)

        val invalidLength = FunctionCall("length", mapOf("value" to "hello world this is too long", "max" to 10))
        assertFalse(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(invalidLength)) as Boolean)
    }

    @Test
    fun testResolveFunctionCall_and() {
        val allTrue = FunctionCall("and", mapOf("values" to listOf(true, true, true)))
        assertTrue(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(allTrue)) as Boolean)

        val someFalse = FunctionCall("and", mapOf("values" to listOf(true, false, true)))
        assertFalse(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(someFalse)) as Boolean)
    }

    @Test
    fun testResolveFunctionCall_or() {
        val someTrue = FunctionCall("or", mapOf("values" to listOf(false, true, false)))
        assertTrue(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(someTrue)) as Boolean)

        val allFalse = FunctionCall("or", mapOf("values" to listOf(false, false, false)))
        assertFalse(processor.resolveDynamicValue("test_surface", DynamicValue.FunctionValue(allFalse)) as Boolean)
    }

    @Test
    fun testClear() {
        processor.createSurface("surface1")
        processor.createSurface("surface2")
        processor.clear()
        assertNull(processor.getDataModel("surface1"))
        assertNull(processor.getDataModel("surface2"))
    }
}
