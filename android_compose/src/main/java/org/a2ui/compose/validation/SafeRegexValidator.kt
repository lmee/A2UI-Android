package org.a2ui.compose.validation

import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * 安全的正则表达式验证器，防止 ReDoS (Regular Expression Denial of Service) 攻击
 *
 * 功能：
 * - 检测危险的正则表达式模式
 * - 限制正则表达式长度
 * - 提供超时保护机制
 * - 防止嵌套量词导致的回溯爆炸
 *
 * @since 0.11
 */
object SafeRegexValidator {
    private const val MAX_PATTERN_LENGTH = 100
    private const val VALIDATION_TIMEOUT_MS = 100L

    /**
     * 已知的危险正则表达式模式列表
     * 这些模式可能导致指数级回溯，造成性能问题
     */
    private val DANGEROUS_PATTERNS = listOf(
        "(.*)*",      // 嵌套星号
        "(.+)+",      // 嵌套加号
        "(a+)+",      // 重复的重复
        "(a*)*",      // 星号的星号
        "(x+x+)+",    // 复杂的重复模式
        "([a-zA-Z]+)*", // 字符类的重复
        "(a|a)*",     // 冗余的选择
        "(a|ab)*"     // 重叠的选择
    )

    /**
     * 验证正则表达式模式是否安全
     *
     * @param pattern 要验证的正则表达式模式
     * @return true 如果模式安全，false 如果模式可能导致 ReDoS
     */
    fun isPatternSafe(pattern: String): Boolean {
        // 1. 检查长度限制
        if (pattern.length > MAX_PATTERN_LENGTH) {
            return false
        }

        // 2. 检查是否包含已知的危险模式
        for (dangerous in DANGEROUS_PATTERNS) {
            if (pattern.contains(dangerous)) {
                return false
            }
        }

        // 3. 检查嵌套量词 (如 *+, +*, ?+, 等)
        val nestedQuantifiers = Regex("""[*+?]\s*[*+?]""")
        if (nestedQuantifiers.containsMatchIn(pattern)) {
            return false
        }

        // 4. 检查过多的分组
        val groupCount = pattern.count { it == '(' }
        if (groupCount > 10) {
            return false
        }

        return true
    }

    /**
     * 安全地执行正则表达式匹配，带超时保护（协程版本）
     *
     * @param pattern 正则表达式模式
     * @param input 要匹配的输入字符串
     * @return true 如果匹配成功，false 如果不匹配，null 如果模式不安全或超时
     */
    suspend fun safeMatches(pattern: String, input: String): Boolean? {
        if (!isPatternSafe(pattern)) {
            return null
        }

        return try {
            withTimeoutOrNull(VALIDATION_TIMEOUT_MS.milliseconds) {
                Regex(pattern).matches(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 安全地执行正则表达式匹配，带超时保护（阻塞版本）
     *
     * 注意：此方法使用线程中断实现超时，可能不如协程版本精确
     *
     * @param pattern 正则表达式模式
     * @param input 要匹配的输入字符串
     * @return true 如果匹配成功，false 如果不匹配，null 如果模式不安全或超时
     */
    fun safeMatchesBlocking(pattern: String, input: String): Boolean? {
        if (!isPatternSafe(pattern)) {
            return null
        }

        return try {
            var result: Boolean? = null
            var exception: Exception? = null

            val thread = Thread {
                try {
                    result = Regex(pattern).matches(input)
                } catch (e: InterruptedException) {
                    // 超时中断，保持 result 为 null
                } catch (e: Exception) {
                    exception = e
                }
            }

            thread.start()
            thread.join(VALIDATION_TIMEOUT_MS)

            if (thread.isAlive) {
                thread.interrupt()
                thread.join(100) // 等待线程清理
                return null  // 超时
            }

            if (exception != null) {
                return null  // 发生异常
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取模式不安全的原因（用于调试和错误消息）
     *
     * @param pattern 要检查的正则表达式模式
     * @return 不安全的原因，如果模式安全则返回 null
     */
    fun getUnsafeReason(pattern: String): String? {
        if (pattern.length > MAX_PATTERN_LENGTH) {
            return "Pattern too long: ${pattern.length} characters (max $MAX_PATTERN_LENGTH)"
        }

        for (dangerous in DANGEROUS_PATTERNS) {
            if (pattern.contains(dangerous)) {
                return "Contains dangerous pattern: $dangerous"
            }
        }

        val nestedQuantifiers = Regex("""[*+?]\s*[*+?]""")
        if (nestedQuantifiers.containsMatchIn(pattern)) {
            return "Contains nested quantifiers"
        }

        val groupCount = pattern.count { it == '(' }
        if (groupCount > 10) {
            return "Too many groups: $groupCount (max 10)"
        }

        return null
    }
}
