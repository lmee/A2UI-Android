package org.a2ui.compose.data

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap

class DataModelState {
    private val _data = mutableStateMapOf<String, Any?>()
    val data: SnapshotStateMap<String, Any?> = _data

    fun updateDataModel(path: String, value: Any?) {
        if (path == "/") {
            _data.clear()
            if (value is Map<*, *>) {
                value.entries.forEach { (k, v) ->
                    _data[k.toString()] = v
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
