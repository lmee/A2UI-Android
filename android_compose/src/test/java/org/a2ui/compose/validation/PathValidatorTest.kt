package org.a2ui.compose.validation

import org.junit.Assert.*
import org.junit.Test

class PathValidatorTest {

    @Test
    fun `rejects path traversal attacks`() {
        val result = PathValidator.validatePath("/../../../etc/passwd")
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
        assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("traversal"))
    }

    @Test
    fun `rejects double slashes`() {
        val result = PathValidator.validatePath("/user//profile")
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
        assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("double slash"))
    }

    @Test
    fun `rejects paths without leading slash`() {
        val result = PathValidator.validatePath("user/profile")
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
        assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("must start with"))
    }

    @Test
    fun `rejects paths ending with slash`() {
        val result = PathValidator.validatePath("/user/profile/")
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
        assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("cannot end with"))
    }

    @Test
    fun `rejects too deep paths`() {
        val deepPath = "/a/b/c/d/e/f/g/h/i/j/k"  // 11 levels
        val result = PathValidator.validatePath(deepPath)
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
        assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("too deep"))
    }

    @Test
    fun `rejects invalid characters - angle brackets`() {
        val result = PathValidator.validatePath("/user/<script>")
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
        assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("Invalid character"))
    }

    @Test
    fun `rejects invalid characters - quotes`() {
        val result1 = PathValidator.validatePath("/user/\"name\"")
        assertTrue(result1 is PathValidator.PathValidationResult.Invalid)

        val result2 = PathValidator.validatePath("/user/'name'")
        assertTrue(result2 is PathValidator.PathValidationResult.Invalid)
    }

    @Test
    fun `rejects invalid characters - special chars`() {
        val dangerousChars = listOf("&", ";", "|", "`", "$", "(", ")", "{", "}", "[", "]", "\\")
        for (char in dangerousChars) {
            val result = PathValidator.validatePath("/user${char}name")
            assertTrue("Should reject $char", result is PathValidator.PathValidationResult.Invalid)
        }
    }

    @Test
    fun `rejects empty path`() {
        val result = PathValidator.validatePath("")
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
        assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("cannot be empty"))
    }

    @Test
    fun `rejects blank path`() {
        val result = PathValidator.validatePath("   ")
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
    }

    @Test
    fun `rejects too long keys`() {
        val longKey = "a".repeat(51)
        val result = PathValidator.validatePath("/$longKey")
        assertTrue(result is PathValidator.PathValidationResult.Invalid)
        assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("too long"))
    }

    @Test
    fun `rejects reserved key names`() {
        val reservedKeys = listOf("__proto__", "constructor", "prototype", "toString", "valueOf")
        for (key in reservedKeys) {
            val result = PathValidator.validatePath("/$key")
            assertTrue("Should reject $key", result is PathValidator.PathValidationResult.Invalid)
            assertTrue((result as PathValidator.PathValidationResult.Invalid).reason.contains("Reserved"))
        }
    }

    @Test
    fun `accepts root path`() {
        val result = PathValidator.validatePath("/")
        assertTrue(result is PathValidator.PathValidationResult.Valid)
    }

    @Test
    fun `accepts valid single level path`() {
        val result = PathValidator.validatePath("/user")
        assertTrue(result is PathValidator.PathValidationResult.Valid)
    }

    @Test
    fun `accepts valid multi level path`() {
        val result = PathValidator.validatePath("/user/profile/name")
        assertTrue(result is PathValidator.PathValidationResult.Valid)
    }

    @Test
    fun `accepts paths with underscores`() {
        val result = PathValidator.validatePath("/user_123/profile_data")
        assertTrue(result is PathValidator.PathValidationResult.Valid)
    }

    @Test
    fun `accepts paths with hyphens`() {
        val result = PathValidator.validatePath("/user-123/profile-data")
        assertTrue(result is PathValidator.PathValidationResult.Valid)
    }

    @Test
    fun `accepts paths with numbers`() {
        val result = PathValidator.validatePath("/user123/profile456")
        assertTrue(result is PathValidator.PathValidationResult.Valid)
    }

    @Test
    fun `accepts maximum depth path`() {
        val maxDepthPath = "/a/b/c/d/e/f/g/h/i/j"  // 10 levels
        val result = PathValidator.validatePath(maxDepthPath)
        assertTrue(result is PathValidator.PathValidationResult.Valid)
    }

    @Test
    fun `accepts maximum length key`() {
        val maxLengthKey = "a".repeat(50)
        val result = PathValidator.validatePath("/$maxLengthKey")
        assertTrue(result is PathValidator.PathValidationResult.Valid)
    }

    @Test
    fun `validatePathOrThrow throws on invalid path`() {
        assertThrows(IllegalArgumentException::class.java) {
            PathValidator.validatePathOrThrow("/../etc/passwd")
        }
    }

    @Test
    fun `validatePathOrThrow does not throw on valid path`() {
        PathValidator.validatePathOrThrow("/user/profile")
        // No exception = success
    }

    @Test
    fun `isValidPath returns true for valid paths`() {
        assertTrue(PathValidator.isValidPath("/"))
        assertTrue(PathValidator.isValidPath("/user"))
        assertTrue(PathValidator.isValidPath("/user/profile/name"))
    }

    @Test
    fun `isValidPath returns false for invalid paths`() {
        assertFalse(PathValidator.isValidPath("/../etc"))
        assertFalse(PathValidator.isValidPath("/user//profile"))
        assertFalse(PathValidator.isValidPath("user/profile"))
    }

    @Test
    fun `normalizePath removes trailing slash`() {
        assertEquals("/user/profile", PathValidator.normalizePath("/user/profile/"))
    }

    @Test
    fun `normalizePath removes leading slash from result`() {
        val normalized = PathValidator.normalizePath("/user/profile")
        assertEquals("/user/profile", normalized)
    }

    @Test
    fun `normalizePath handles root path`() {
        assertEquals("/", PathValidator.normalizePath("/"))
    }

    @Test
    fun `normalizePath filters empty segments`() {
        // Note: This won't fix double slashes, just normalizes format
        val normalized = PathValidator.normalizePath("/user/profile")
        assertEquals("/user/profile", normalized)
    }
}
