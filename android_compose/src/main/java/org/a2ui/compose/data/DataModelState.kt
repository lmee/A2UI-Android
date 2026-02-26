package org.a2ui.compose.data

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.a2ui.compose.validation.PathValidator

class DataModelState {
    private val _data = mutableStateMapOf<String, Any?>()
    val data: SnapshotStateMap<String, Any?> = _data

    companion object {
        const val MAX_ENTRIES = 10_000
    }

    fun updateDataModel(path: String, value: Any?) {
        // ✅ 验证路径安全性
        PathValidator.validatePathOrThrow(path)

        // ✅ 检查条目数上限
        if (_data.size >= MAX_ENTRIES && path != "/") {
            val keys = path.removePrefix("/").split("/")
            if (!_data.containsKey(keys.first())) {
                throw IllegalStateException("Data model entry limit ($MAX_ENTRIES) exceeded")
            }
        }

        if (path == "/") {
            _data.clear()
            if (value is Map<*, *>) {
                value.entries.forEach { (k, v) ->
                    val key = k.toString()
                    // ✅ 验证每个键名
                    if (PathValidator.isValidPath("/$key")) {
                        _data[key] = v
                    }
                }
            }
        } else {
            val keys = path.removePrefix("/").split("/")
            updateNestedValue(_data, keys, 0, value)
        }
    }

    private fun updateNestedValue(map: SnapshotStateMap<String, Any?>, keys: List<String>, index: Int, value: Any?) {
        if (index == keys.size - 1) {
            if (value == null) {
                map.remove(keys[index])
            } else {
                map[keys[index]] = value
            }
        } else {
            val key = keys[index]
            val nextMap = map[key] as? SnapshotStateMap<String, Any?> ?: mutableStateMapOf()
            map[key] = nextMap
            updateNestedValue(nextMap, keys, index + 1, value)
        }
    }

    fun getValue(path: String): Any? {
        // ✅ 验证路径安全性，无效路径返回 null
        if (!PathValidator.isValidPath(path)) {
            return null
        }

        if (path == "/") {
            return _data
        }

        val keys = path.removePrefix("/").split("/")
        return getNestedValue(_data, keys, 0)
    }

    private fun getNestedValue(map: Map<String, Any?>, keys: List<String>, index: Int): Any? {
        if (index == keys.size) {
            return map
        }

        val key = keys[index]
        val value = map[key]

        if (index == keys.size - 1) {
            return value
        }

        if (value is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return getNestedValue(value as Map<String, Any?>, keys, index + 1)
        }

        return null
    }

    fun clear() {
        _data.clear()
    }

    fun getDataSnapshot(): Map<String, Any?> {
        return _data.toMap()
    }
}
