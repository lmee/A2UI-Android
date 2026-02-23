package org.a2ui.compose.validation

/**
 * 数据模型路径验证器，防止路径遍历攻击
 *
 * 功能：
 * - 验证路径格式的合法性
 * - 防止路径遍历攻击 (../)
 * - 限制路径深度和键名长度
 * - 只允许安全的字符集
 *
 * @since 0.11
 */
object PathValidator {
    private const val MAX_PATH_DEPTH = 10
    private const val MAX_KEY_LENGTH = 50

    /**
     * 允许的键名字符：字母、数字、下划线、连字符
     */
    private val VALID_KEY_REGEX = "^[a-zA-Z0-9_-]+$".toRegex()

    /**
     * 验证数据模型路径是否安全
     *
     * @param path 要验证的路径（如 "/user/profile/name"）
     * @return PathValidationResult.Valid 如果路径安全，PathValidationResult.Invalid 如果路径不安全
     */
    fun validatePath(path: String): PathValidationResult {
        // 1. 空路径检查
        if (path.isBlank()) {
            return PathValidationResult.Invalid("Path cannot be empty")
        }

        // 2. 根路径特殊处理
        if (path == "/") {
            return PathValidationResult.Valid
        }

        // 3. 必须以 / 开头
        if (!path.startsWith("/")) {
            return PathValidationResult.Invalid("Path must start with /")
        }

        // 4. 不能以 / 结尾（除非是根路径）
        if (path.endsWith("/") && path != "/") {
            return PathValidationResult.Invalid("Path cannot end with /")
        }

        // 5. 检查路径遍历攻击
        if (path.contains("..")) {
            return PathValidationResult.Invalid("Path traversal detected: ..")
        }

        // 6. 检查双斜杠
        if (path.contains("//")) {
            return PathValidationResult.Invalid("Invalid path: double slash")
        }

        // 7. 检查其他危险字符
        val dangerousChars = listOf("<", ">", "\"", "'", "&", ";", "|", "`", "$", "(", ")", "{", "}", "[", "]", "\\")
        for (char in dangerousChars) {
            if (path.contains(char)) {
                return PathValidationResult.Invalid("Invalid character in path: $char")
            }
        }

        // 8. 分割并验证每个键
        val keys = path.removePrefix("/").split("/")

        // 9. 检查深度
        if (keys.size > MAX_PATH_DEPTH) {
            return PathValidationResult.Invalid("Path too deep: ${keys.size} levels (max $MAX_PATH_DEPTH)")
        }

        // 10. 验证每个键
        for ((index, key) in keys.withIndex()) {
            if (key.isEmpty()) {
                return PathValidationResult.Invalid("Empty key at position $index")
            }

            if (key.length > MAX_KEY_LENGTH) {
                return PathValidationResult.Invalid("Key too long at position $index: ${key.length} characters (max $MAX_KEY_LENGTH)")
            }

            if (!VALID_KEY_REGEX.matches(key)) {
                return PathValidationResult.Invalid("Invalid characters in key at position $index: $key (only alphanumeric, underscore, and hyphen allowed)")
            }

            // 11. 禁止特殊的保留键名
            if (key in RESERVED_KEYS) {
                return PathValidationResult.Invalid("Reserved key name at position $index: $key")
            }
        }

        return PathValidationResult.Valid
    }

    /**
     * 保留的键名列表（不允许使用）
     */
    private val RESERVED_KEYS = setOf(
        "__proto__",
        "constructor",
        "prototype",
        "toString",
        "valueOf"
    )

    /**
     * 路径验证结果
     */
    sealed class PathValidationResult {
        /**
         * 路径有效
         */
        object Valid : PathValidationResult()

        /**
         * 路径无效
         * @param reason 无效的原因
         */
        data class Invalid(val reason: String) : PathValidationResult()
    }

    /**
     * 验证路径并抛出异常（如果无效）
     *
     * @param path 要验证的路径
     * @throws IllegalArgumentException 如果路径无效
     */
    fun validatePathOrThrow(path: String) {
        val result = validatePath(path)
        if (result is PathValidationResult.Invalid) {
            throw IllegalArgumentException("Invalid path: ${result.reason}")
        }
    }

    /**
     * 检查路径是否有效（简化版本）
     *
     * @param path 要检查的路径
     * @return true 如果路径有效，false 如果路径无效
     */
    fun isValidPath(path: String): Boolean {
        return validatePath(path) is PathValidationResult.Valid
    }

    /**
     * 规范化路径（移除多余的斜杠等）
     *
     * 注意：此方法不会修复无效路径，只会清理格式
     *
     * @param path 要规范化的路径
     * @return 规范化后的路径
     */
    fun normalizePath(path: String): String {
        if (path == "/") return path

        return path
            .removePrefix("/")
            .removeSuffix("/")
            .split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/", prefix = "/")
    }
}
