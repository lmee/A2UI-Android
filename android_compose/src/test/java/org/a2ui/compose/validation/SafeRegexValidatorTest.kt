package org.a2ui.compose.validation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SafeRegexValidatorTest {

    @Test
    fun `rejects dangerous nested quantifiers`() {
        assertFalse(SafeRegexValidator.isPatternSafe("(a+)+"))
        assertFalse(SafeRegexValidator.isPatternSafe("(.*)*"))
        assertFalse(SafeRegexValidator.isPatternSafe("(a|a)*"))
        assertFalse(SafeRegexValidator.isPatternSafe("(.+)+"))
        assertFalse(SafeRegexValidator.isPatternSafe("(a*)*"))
    }

    @Test
    fun `rejects overly long patterns`() {
        val longPattern = "a".repeat(101)
        assertFalse(SafeRegexValidator.isPatternSafe(longPattern))
    }

    @Test
    fun `rejects nested quantifiers`() {
        assertFalse(SafeRegexValidator.isPatternSafe("a*+"))
        assertFalse(SafeRegexValidator.isPatternSafe("a+*"))
        assertFalse(SafeRegexValidator.isPatternSafe("a?+"))
    }

    @Test
    fun `rejects too many groups`() {
        val manyGroups = "(a)" + "(b)".repeat(10)
        assertFalse(SafeRegexValidator.isPatternSafe(manyGroups))
    }

    @Test
    fun `accepts safe patterns`() {
        assertTrue(SafeRegexValidator.isPatternSafe("^[A-Z][a-z]+$"))
        assertTrue(SafeRegexValidator.isPatternSafe("\\d{3}-\\d{4}"))
        assertTrue(SafeRegexValidator.isPatternSafe("[a-zA-Z0-9]+"))
        assertTrue(SafeRegexValidator.isPatternSafe("^\\w+@\\w+\\.\\w+$"))
    }

    @Test
    fun `safeMatchesBlocking returns null for unsafe patterns`() {
        val result = SafeRegexValidator.safeMatchesBlocking("(a+)+", "aaaa")
        assertNull(result)
    }

    @Test
    fun `safeMatchesBlocking returns true for valid match`() {
        val result = SafeRegexValidator.safeMatchesBlocking("^[A-Z][a-z]+$", "Hello")
        assertEquals(true, result)
    }

    @Test
    fun `safeMatchesBlocking returns false for invalid match`() {
        val result = SafeRegexValidator.safeMatchesBlocking("^[A-Z][a-z]+$", "hello")
        assertEquals(false, result)
    }

    @Test
    fun `safeMatchesBlocking handles invalid regex`() {
        val result = SafeRegexValidator.safeMatchesBlocking("[", "test")
        assertNull(result)
    }

    @Test
    fun `safeMatches coroutine version works`() = runBlocking {
        val result = SafeRegexValidator.safeMatches("^[A-Z][a-z]+$", "Hello")
        assertEquals(true, result)
    }

    @Test
    fun `safeMatches returns null for unsafe pattern`() = runBlocking {
        val result = SafeRegexValidator.safeMatches("(a+)+", "aaaa")
        assertNull(result)
    }

    @Test
    fun `getUnsafeReason returns correct reason for long pattern`() {
        val longPattern = "a".repeat(101)
        val reason = SafeRegexValidator.getUnsafeReason(longPattern)
        assertNotNull(reason)
        assertTrue(reason!!.contains("too long"))
    }

    @Test
    fun `getUnsafeReason returns correct reason for dangerous pattern`() {
        val reason = SafeRegexValidator.getUnsafeReason("(a+)+")
        assertNotNull(reason)
        assertTrue(reason!!.contains("dangerous pattern"))
    }

    @Test
    fun `getUnsafeReason returns correct reason for nested quantifiers`() {
        val reason = SafeRegexValidator.getUnsafeReason("a*+")
        assertNotNull(reason)
        assertTrue(reason!!.contains("nested quantifiers"))
    }

    @Test
    fun `getUnsafeReason returns correct reason for too many groups`() {
        val manyGroups = "(a)" + "(b)".repeat(10)
        val reason = SafeRegexValidator.getUnsafeReason(manyGroups)
        assertNotNull(reason)
        assertTrue(reason!!.contains("Too many groups"))
    }

    @Test
    fun `getUnsafeReason returns null for safe pattern`() {
        val reason = SafeRegexValidator.getUnsafeReason("^[A-Z][a-z]+$")
        assertNull(reason)
    }

    @Test
    fun `handles empty pattern`() {
        assertTrue(SafeRegexValidator.isPatternSafe(""))
        val result = SafeRegexValidator.safeMatchesBlocking("", "test")
        assertEquals(false, result)
    }

    @Test
    fun `handles empty input`() {
        val result = SafeRegexValidator.safeMatchesBlocking("^[A-Z]+$", "")
        assertEquals(false, result)
    }

    @Test
    fun `common email pattern is safe`() {
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        assertTrue(SafeRegexValidator.isPatternSafe(emailPattern))

        val result = SafeRegexValidator.safeMatchesBlocking(emailPattern, "user@example.com")
        assertEquals(true, result)
    }

    @Test
    fun `common phone pattern is safe`() {
        val phonePattern = "^[+]?[0-9]{10,15}$"
        assertTrue(SafeRegexValidator.isPatternSafe(phonePattern))

        val result = SafeRegexValidator.safeMatchesBlocking(phonePattern, "+1234567890")
        assertEquals(true, result)
    }

    @Test
    fun `common URL pattern is safe`() {
        val urlPattern = "^(https?://)?([\\w.-]+)(\\.[\\w.-]+)+[/#?]?.*$"
        assertTrue(SafeRegexValidator.isPatternSafe(urlPattern))

        val result = SafeRegexValidator.safeMatchesBlocking(urlPattern, "https://example.com")
        assertEquals(true, result)
    }
}
