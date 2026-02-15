package org.a2ui.compose.transport

import kotlinx.coroutines.flow.Flow

sealed class TransportState {
    object Disconnected : TransportState()
    object Connecting : TransportState()
    object Connected : TransportState()
    data class Error(val message: String) : TransportState()
}

interface A2UITransport {
    val state: Flow<TransportState>
    val messages: Flow<String>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(message: String)
}

interface A2UIActionSender {
    suspend fun sendAction(
        surfaceId: String,
        actionName: String,
        context: Map<String, Any>,
        dataModel: Map<String, Any?>? = null
    )
}
