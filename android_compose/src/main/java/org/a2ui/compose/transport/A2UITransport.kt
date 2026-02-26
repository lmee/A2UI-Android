package org.a2ui.compose.transport

import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

object A2UIHttpClientFactory {
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
