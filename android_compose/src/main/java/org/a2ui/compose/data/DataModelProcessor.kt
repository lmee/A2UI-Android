package org.a2ui.compose.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.a2ui.compose.validation.SafeRegexValidator
import java.util.concurrent.ConcurrentHashMap

class DataModelProcessor {
    private val surfaces = ConcurrentHashMap<String, DataModelState>()

    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
            .toRegex(RegexOption.IGNORE_CASE)
        private val URL_REGEX = "^(https?://)?([\\w.-]+)(\\.[\\w.-]+)+[/#?]?.*$"
            .toRegex(RegexOption.IGNORE_CASE)
        private val PHONE_REGEX = "^[+]?[0-9]{10,15}$".toRegex()
        private val PHONE_CLEAN_REGEX = "[\\s-()]+".toRegex()
        private val FORMAT_STRING_REGEX = """\$\{([^}]+)\}""".toRegex()
        private val FUNC_ARGS_REGEX = """(\w+):\s*([^,]+)""".toRegex()
        private const val MAX_PATH_DEPTH = 10
        private const val MAX_DATE_FORMAT_LENGTH = 64
        private val SAFE_DATE_FORMAT_REGEX = """^[a-zA-Z0-9\s\-/:.,'+\[\]()]+$""".toRegex()
    }

    fun createSurface(surfaceId: String) {
        if (!surfaces.containsKey(surfaceId)) {
            surfaces[surfaceId] = DataModelState()
        }
    }

    fun deleteSurface(surfaceId: String) {
        surfaces.remove(surfaceId)
    }

    fun updateDataModel(surfaceId: String, path: String, value: Any?) {
        val surfaceData = surfaces[surfaceId] ?: return
        surfaceData.updateDataModel(path, value)
    }

    fun getValue(surfaceId: String, path: String): Any? {
        val surfaceData = surfaces[surfaceId] ?: return null
        return surfaceData.getValue(path)
    }

    fun getDataModel(surfaceId: String): DataModelState? {
        return surfaces[surfaceId]
    }

    fun getSurfaceData(surfaceId: String): Map<String, Any?>? {
        return surfaces[surfaceId]?.getDataSnapshot()
    }

    fun resolveDynamicValue(surfaceId: String, value: DynamicValue<*>?): Any? {
        return resolveDynamicValueWithScope(surfaceId, value, null)
    }

    /**
     * 带作用域的动态值解析（支持 Collection Scope）
     *
     * 当 scopePath 不为 null 时，相对路径（不以 / 开头）会在 scopePath 下解析。
     * 例如 scopePath="/users/0"，相对路径 "name" 解析为 "/users/0/name"
     */
    fun resolveDynamicValueWithScope(surfaceId: String, value: DynamicValue<*>?, scopePath: String?): Any? {
        if (value == null) return null

        return when (value) {
            is DynamicValue.LiteralValue<*> -> value.literal
            is DynamicValue.PathValue<*> -> {
                val resolvedPath = if (scopePath != null && !value.path.startsWith("/")) {
                    // 相对路径：在 scopePath 下解析
                    "$scopePath/${value.path}"
                } else {
                    // 绝对路径：直接解析
                    value.path
                }
                getValue(surfaceId, resolvedPath)
            }
            is DynamicValue.FunctionValue<*> -> resolveFunctionCall(value.functionCall)
        }
    }

    private fun resolveFunctionCall(functionCall: FunctionCall): Any? {
        return when (functionCall.call) {
            "formatString" -> {
                val value = functionCall.args["value"] as? String ?: ""
                formatString(value, functionCall.args)
            }
            "required" -> {
                val value = functionCall.args["value"]
                value != null && value != "" && value != false
            }
            "email" -> {
                val value = functionCall.args["value"] as? String ?: ""
                isValidEmail(value)
            }
            "regex" -> {
                val value = functionCall.args["value"] as? String ?: ""
                val pattern = functionCall.args["pattern"] as? String ?: ""
                SafeRegexValidator.safeMatchesBlocking(pattern, value) ?: false
            }
            "numeric" -> {
                val value = functionCall.args["value"]
                when (value) {
                    is Number -> {
                        val min = (functionCall.args["min"] as? Number)?.toDouble()
                        val max = (functionCall.args["max"] as? Number)?.toDouble()
                        val num = value.toDouble()
                        (min == null || num >= min) && (max == null || num <= max)
                    }
                    else -> false
                }
            }
            "length" -> {
                val value = functionCall.args["value"] as? String ?: ""
                val min = functionCall.args["min"] as? Int
                val max = functionCall.args["max"] as? Int
                val len = value.length
                (min == null || len >= min) && (max == null || len <= max)
            }
            "and" -> {
                val values = functionCall.args["values"] as? List<*> ?: return true
                values.all { it == true }
            }
            "or" -> {
                val values = functionCall.args["values"] as? List<*> ?: return false
                values.any { it == true }
            }
            "not" -> {
                val value = functionCall.args["value"]
                value != true
            }
            "min" -> {
                val value = functionCall.args["value"] as? Number
                val minValue = (functionCall.args["min"] as? Number)?.toDouble()
                value != null && minValue != null && value.toDouble() >= minValue
            }
            "max" -> {
                val value = functionCall.args["value"] as? Number
                val maxValue = (functionCall.args["max"] as? Number)?.toDouble()
                value != null && maxValue != null && value.toDouble() <= maxValue
            }
            "url" -> {
                val value = functionCall.args["value"] as? String ?: ""
                isValidUrl(value)
            }
            "phone" -> {
                val value = functionCall.args["value"] as? String ?: ""
                isValidPhone(value)
            }
            "formatNumber" -> {
                val value = (functionCall.args["value"] as? Number)?.toDouble() ?: return null
                val decimals = (functionCall.args["decimals"] as? Number)?.toInt()
                val grouping = functionCall.args["grouping"] as? Boolean ?: true
                formatNumber(value, decimals, grouping)
            }
            "formatCurrency" -> {
                val value = (functionCall.args["value"] as? Number)?.toDouble() ?: return null
                val currency = functionCall.args["currency"] as? String ?: "USD"
                val decimals = (functionCall.args["decimals"] as? Number)?.toInt() ?: 2
                val grouping = functionCall.args["grouping"] as? Boolean ?: true
                formatCurrency(value, currency, decimals, grouping)
            }
            "formatDate" -> {
                val value = functionCall.args["value"] as? String ?: return null
                val format = functionCall.args["format"] as? String ?: "yyyy-MM-dd"
                formatDate(value, format)
            }
            "pluralize" -> {
                val value = (functionCall.args["value"] as? Number)?.toDouble() ?: return null
                val zero = functionCall.args["zero"] as? String
                val one = functionCall.args["one"] as? String
                val two = functionCall.args["two"] as? String
                val few = functionCall.args["few"] as? String
                val many = functionCall.args["many"] as? String
                val other = functionCall.args["other"] as? String ?: ""
                pluralize(value, zero, one, two, few, many, other)
            }
            "openUrl" -> {
                // openUrl is a side-effect function, handled by ActionHandler
                functionCall.args["url"] as? String
            }
            else -> null
        }
    }

    private fun formatNumber(value: Double, decimals: Int?, grouping: Boolean): String {
        val nf = java.text.NumberFormat.getNumberInstance()
        if (decimals != null) {
            nf.minimumFractionDigits = decimals
            nf.maximumFractionDigits = decimals
        }
        nf.isGroupingUsed = grouping
        return nf.format(value)
    }

    private fun formatCurrency(value: Double, currencyCode: String, decimals: Int, grouping: Boolean): String {
        return try {
            val nf = java.text.NumberFormat.getCurrencyInstance()
            nf.currency = java.util.Currency.getInstance(currencyCode)
            nf.minimumFractionDigits = decimals
            nf.maximumFractionDigits = decimals
            nf.isGroupingUsed = grouping
            nf.format(value)
        } catch (e: Exception) {
            "$currencyCode ${formatNumber(value, decimals, grouping)}"
        }
    }

    private fun formatDate(value: String, format: String): String {
        // ✅ 校验 format 字符串长度和字符集
        if (format.length > MAX_DATE_FORMAT_LENGTH || !SAFE_DATE_FORMAT_REGEX.matches(format)) {
            return value
        }

        return try {
            val inputFormats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            )
            var date: java.util.Date? = null
            for (fmt in inputFormats) {
                try {
                    fmt.isLenient = false
                    date = fmt.parse(value)
                    if (date != null) break
                } catch (_: Exception) {}
            }
            if (date == null) return value
            val outputFormat = java.text.SimpleDateFormat(format, java.util.Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            value
        }
    }

    /**
     * CLDR 复数规则（简化版，覆盖英语等常见语言）
     */
    private fun pluralize(
        value: Double, zero: String?, one: String?,
        two: String?, few: String?, many: String?, other: String
    ): String {
        val intVal = value.toInt()
        return when {
            value == 0.0 && zero != null -> zero
            value == 1.0 && one != null -> one
            value == 2.0 && two != null -> two
            intVal in 3..10 && few != null -> few
            intVal > 10 && many != null -> many
            else -> other
        }
    }

    private fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        return EMAIL_REGEX.matches(email)
    }

    private fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return URL_REGEX.matches(url)
    }

    private fun isValidPhone(phone: String): Boolean {
        if (phone.isBlank()) return false
        val cleanedPhone = phone.replace(PHONE_CLEAN_REGEX, "")
        return PHONE_REGEX.matches(cleanedPhone)
    }

    private fun formatString(template: String, args: Map<String, Any>): String {
        var result = template

        return result.replace(FORMAT_STRING_REGEX) { matchResult ->
            val expression = matchResult.groupValues[1]
            when {
                expression.startsWith("/") -> {
                    val path = expression
                    args["_dataModel"]?.let { dataModel ->
                        resolvePath(dataModel, path)?.toString() ?: ""
                    } ?: ""
                }
                expression.contains("(") && expression.endsWith(")") -> {
                    val funcName = expression.substringBefore("(")
                    val funcArgs = parseFunctionArgs(expression)
                    val funcResult = callFunction(funcName, funcArgs)
                    funcResult?.toString() ?: ""
                }
                else -> expression
            }
        }
    }

    private fun resolvePath(dataModel: Any, path: String): Any? {
        val cleanPath = path.removePrefix("/")
        val keys = cleanPath.split("/")
        if (keys.size > MAX_PATH_DEPTH) return null

        var current: Any? = dataModel
        for (key in keys) {
            current = when (current) {
                is Map<*, *> -> current[key]
                is List<*> -> {
                    val index = key.toIntOrNull()
                    if (index != null && index in current.indices) current[index] else null
                }
                else -> null
            }
        }
        return current
    }

    private fun parseFunctionArgs(expression: String): Map<String, Any> {
        val argsStr = expression.substringAfter("(").substringBefore(")")
        if (argsStr.isBlank()) return emptyMap()

        val args = mutableMapOf<String, Any>()

        FUNC_ARGS_REGEX.findAll(argsStr).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim()
            args[key] = value
        }

        return args
    }

    private fun callFunction(name: String, args: Map<String, Any>): Any? {
        return when (name) {
            "now" -> System.currentTimeMillis()
            "upper" -> args["value"]?.toString()?.uppercase()
            "lower" -> args["value"]?.toString()?.lowercase()
            "capitalize" -> args["value"]?.toString()?.replaceFirstChar { it.uppercase() }
            "trim" -> args["value"]?.toString()?.trim()
            "length" -> args["value"]?.toString()?.length
            "isEmpty" -> args["value"]?.toString()?.isEmpty()
            "isNotEmpty" -> args["value"]?.toString()?.isNotEmpty()
            else -> null
        }
    }

    fun clear() {
        surfaces.clear()
    }
}
