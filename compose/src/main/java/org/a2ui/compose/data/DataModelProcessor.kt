package org.a2ui.compose.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap

class DataModelProcessor {
    private val surfaces = mutableMapOf<String, DataModelState>()

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
        if (value == null) return null

        return when (value) {
            is DynamicValue.LiteralValue<*> -> value.literal
            is DynamicValue.PathValue<*> -> getValue(surfaceId, value.path)
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
                try {
                    Regex(pattern).matches(value)
                } catch (e: Exception) {
                    false
                }
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
            else -> null
        }
    }

    private fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex(RegexOption.IGNORE_CASE)
        return emailRegex.matches(email)
    }

    private fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val urlRegex = "^(https?://)?([\\w.-]+)(\\.[\\w.-]+)+[/#?]?.*$".toRegex(RegexOption.IGNORE_CASE)
        return urlRegex.matches(url)
    }

    private fun isValidPhone(phone: String): Boolean {
        if (phone.isBlank()) return false
        val phoneRegex = "^[+]?[0-9]{10,15}$".toRegex(RegexOption.IGNORE_CASE)
        val cleanedPhone = phone.replace(Regex("[\\s-()]+"), "")
        return phoneRegex.matches(cleanedPhone)
    }

    private fun formatString(template: String, args: Map<String, Any>): String {
        var result = template
        val pattern = """\$\{([^}]+)\}""".toRegex()

        return result.replace(pattern) { matchResult ->
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
        val pattern = """(\w+):\s*([^,]+)""".toRegex()

        pattern.findAll(argsStr).forEach { match ->
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
