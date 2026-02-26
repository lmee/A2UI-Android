package org.a2ui.compose.rendering

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.a2ui.compose.data.*
import org.a2ui.compose.data.ChildList.ArrayChildList
import org.a2ui.compose.error.*

sealed class A2UIRendererState {
    object Idle : A2UIRendererState()
    object Loading : A2UIRendererState()
    data class Error(val message: String, val error: A2UIError? = null) : A2UIRendererState()
}

data class SavedRendererState(
    val surfaces: Map<String, SurfaceContext>,
    val dataModels: Map<String, Map<String, Any?>>,
    val components: Map<String, Map<String, Component>>
)

class A2UIRenderer(
    private val logger: A2UILogger = DefaultLogger(),
    private val errorHandler: A2UIErrorHandler? = null
) {
    private val dataModelProcessor = DataModelProcessor()
    val registry = ComponentRegistry(this)
    private val surfaces = mutableStateMapOf<String, SurfaceContext>()
    private val surfaceComponents = mutableStateMapOf<String, SnapshotStateMap<String, Component>>()
    private val surfaceStates = mutableStateMapOf<String, A2UIRendererState>()

    private val _actionHandler = MutableStateFlow<ActionHandler?>(null)
    val actionHandler: StateFlow<ActionHandler?>
        get() = _actionHandler.asStateFlow()

    private val _errors = mutableStateListOf<A2UIError>()
    val errors: List<A2UIError> get() = _errors.toList()

    companion object {
        const val MAX_MESSAGE_SIZE = 1 * 1024 * 1024  // 1MB
        const val MAX_COMPONENTS_PER_SURFACE = 1000
        const val MAX_SURFACES = 50
        const val MAX_ERROR_COUNT = 100
        val ALLOWED_URL_SCHEMES = setOf("https://", "http://", "mailto:", "tel:", "sms:")

        val Saver: Saver<A2UIRenderer, SavedRendererState> = Saver(
            save = { renderer -> renderer.saveState() },
            restore = { savedState ->
                A2UIRenderer().apply { restoreState(savedState) }
            }
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        serializersModule = SerializersModule {
            contextual(Any::class, AnySerializer)
        }
    }

    private val surfaceChanges = mutableStateMapOf<String, MutableStateFlow<Unit>>()

    fun setActionHandler(handler: ActionHandler?) {
        _actionHandler.value = handler
    }

    fun processMessage(message: String): Result<Unit> {
        return try {
            if (message.isBlank()) {
                val error = A2UIError.ParseError("Empty message", message)
                handleError(error, "unknown")
                return Result.failure(IllegalArgumentException("Message cannot be empty"))
            }

            if (message.length > MAX_MESSAGE_SIZE) {
                val error = A2UIError.ParseError("Message too large: ${message.length} bytes")
                handleError(error, "unknown")
                return Result.failure(IllegalArgumentException("Message exceeds size limit"))
            }

            // Parse JSON once, reuse for type detection and deserialization
            val jsonObj = try {
                json.parseToJsonElement(message).jsonObject
            } catch (e: Exception) {
                val error = A2UIError.ParseError("Invalid JSON: ${e.message}", message)
                handleError(error, "unknown")
                return Result.failure(e)
            }

            val surfaceId = jsonObj["createSurface"]?.jsonObject?.get("surfaceId")?.jsonPrimitive?.content
                ?: jsonObj["updateComponents"]?.jsonObject?.get("surfaceId")?.jsonPrimitive?.content
                ?: jsonObj["updateDataModel"]?.jsonObject?.get("surfaceId")?.jsonPrimitive?.content
                ?: jsonObj["deleteSurface"]?.jsonObject?.get("surfaceId")?.jsonPrimitive?.content
                ?: "unknown"

            val a2uiMessage = try {
                when {
                    "createSurface" in jsonObj -> json.decodeFromJsonElement<CreateSurfaceMessage>(jsonObj)
                    "updateComponents" in jsonObj -> json.decodeFromJsonElement<UpdateComponentsMessage>(jsonObj)
                    "updateDataModel" in jsonObj -> json.decodeFromJsonElement<UpdateDataModelMessage>(jsonObj)
                    "deleteSurface" in jsonObj -> json.decodeFromJsonElement<DeleteSurfaceMessage>(jsonObj)
                    else -> throw SerializationException("Unknown message type: no recognized key found")
                }
            } catch (e: SerializationException) {
                val error = A2UIError.ParseError("Invalid JSON format: ${e.message}", message)
                handleError(error, surfaceId)
                return Result.failure(e)
            }

            // Batch all snapshot state mutations into a single atomic update
            // so Compose sees one recomposition instead of N separate ones
            Snapshot.withMutableSnapshot {
                when (a2uiMessage) {
                    is CreateSurfaceMessage -> handleCreateSurface(a2uiMessage.createSurface)
                    is UpdateComponentsMessage -> handleUpdateComponents(a2uiMessage.updateComponents)
                    is UpdateDataModelMessage -> handleUpdateDataModel(a2uiMessage.updateDataModel)
                    is DeleteSurfaceMessage -> handleDeleteSurface(a2uiMessage.deleteSurface)
                }
                surfaceStates[surfaceId] = A2UIRendererState.Idle
            }
            surfaceChanges[surfaceId]?.value = Unit
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            logger.log(A2UILogLevel.ERROR, "Invalid argument: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            logger.log(A2UILogLevel.ERROR, "Error processing message: ${e.message}")
            Result.failure(e)
        }
    }

    private fun handleError(error: A2UIError, surfaceId: String) {
        // ✅ FIFO 淘汰
        while (_errors.size >= MAX_ERROR_COUNT) {
            _errors.removeFirst()
        }
        _errors.add(error)
        surfaceStates[surfaceId] = A2UIRendererState.Error(
            message = getErrorMessage(error),
            error = error
        )
        errorHandler?.handleError(error)
    }

    fun clearErrors() {
        _errors.clear()
        errorHandler?.clearErrors()
    }

    fun dismissError(index: Int) {
        if (index in _errors.indices) {
            _errors.removeAt(index)
        }
    }

    private fun getSurfaceId(message: String): String {
        return try {
            val jsonObj = json.parseToJsonElement(message)
            val surfaceId = jsonObj.jsonObject["createSurface"]?.jsonObject?.get("surfaceId")?.jsonPrimitive?.content
                ?: jsonObj.jsonObject["updateComponents"]?.jsonObject?.get("surfaceId")?.jsonPrimitive?.content
                ?: jsonObj.jsonObject["updateDataModel"]?.jsonObject?.get("surfaceId")?.jsonPrimitive?.content
                ?: jsonObj.jsonObject["deleteSurface"]?.jsonObject?.get("surfaceId")?.jsonPrimitive?.content
            surfaceId ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun handleCreateSurface(createSurface: CreateSurface) {
        logger.log(A2UILogLevel.INFO, "Creating surface: ${createSurface.surfaceId}")
        if (surfaces.size >= MAX_SURFACES) {
            logger.log(A2UILogLevel.WARN, "Surface limit reached: ${surfaces.size}")
            throw IllegalStateException("Maximum surface count ($MAX_SURFACES) exceeded")
        }
        val surfaceId = createSurface.surfaceId
        // ✅ 校验 surfaceId 格式
        if (!A2UIProtocol.isValidId(surfaceId)) {
            logger.log(A2UILogLevel.WARN, "Invalid surfaceId format: $surfaceId")
            throw IllegalArgumentException("Invalid surfaceId format: $surfaceId")
        }
        // ✅ 重复创建 warn
        if (surfaces.containsKey(surfaceId)) {
            logger.log(A2UILogLevel.WARN, "Surface already exists, overwriting: $surfaceId")
        }
        dataModelProcessor.createSurface(surfaceId)
        surfaces[surfaceId] = SurfaceContext(
            surfaceId = surfaceId,
            catalogId = createSurface.catalogId,
            theme = createSurface.theme,
            sendDataModel = createSurface.sendDataModel
        )
        surfaceComponents[surfaceId] = mutableStateMapOf()
        surfaceStates[surfaceId] = A2UIRendererState.Idle
        surfaceChanges[surfaceId] = MutableStateFlow(Unit)
    }

    private fun handleUpdateComponents(updateComponents: UpdateComponents) {
        val surfaceId = updateComponents.surfaceId
        val components = updateComponents.components
        val componentMap = surfaceComponents[surfaceId] ?: run {
            logger.log(A2UILogLevel.WARN, "Surface not found: $surfaceId")
            return
        }

        val currentCount = componentMap.size
        if (currentCount + components.size > MAX_COMPONENTS_PER_SURFACE) {
            logger.log(A2UILogLevel.WARN, "Component limit exceeded for surface: $surfaceId")
            throw IllegalStateException("Maximum component count ($MAX_COMPONENTS_PER_SURFACE) exceeded")
        }

        components.forEach { component ->
            // ✅ 校验 component.id 格式
            if (!A2UIProtocol.isValidId(component.id)) {
                logger.log(A2UILogLevel.WARN, "Invalid component id format: ${component.id}, skipping")
                return@forEach
            }
            componentMap[component.id] = component
        }

        logger.log(A2UILogLevel.DEBUG, "Updated ${components.size} components in surface: $surfaceId")
    }

    private fun handleUpdateDataModel(updateDataModel: UpdateDataModel) {
        logger.log(A2UILogLevel.DEBUG, "Updating data model: ${updateDataModel.surfaceId} at path ${updateDataModel.path}")
        dataModelProcessor.updateDataModel(
            updateDataModel.surfaceId,
            updateDataModel.path,
            updateDataModel.value
        )
    }

    private fun handleDeleteSurface(deleteSurface: DeleteSurface) {
        logger.log(A2UILogLevel.INFO, "Deleting surface: ${deleteSurface.surfaceId}")
        val surfaceId = deleteSurface.surfaceId
        dataModelProcessor.deleteSurface(surfaceId)
        surfaces.remove(surfaceId)
        surfaceComponents.remove(surfaceId)
        surfaceStates.remove(surfaceId)
        surfaceChanges.remove(surfaceId)
    }

    fun updateDataModel(surfaceId: String, path: String, value: Any?) {
        dataModelProcessor.updateDataModel(surfaceId, path, value)
        surfaceChanges[surfaceId]?.value = Unit
    }

    fun resolveValue(surfaceId: String, value: DynamicValue<*>?): Any? {
        return dataModelProcessor.resolveDynamicValue(surfaceId, value)
    }

    /**
     * 带作用域的值解析，用于 template 渲染
     */
    fun resolveValueWithScope(surfaceId: String, value: DynamicValue<*>?, scopePath: String?): Any? {
        return dataModelProcessor.resolveDynamicValueWithScope(surfaceId, value, scopePath)
    }

    fun getComponent(surfaceId: String, componentId: String): Component? {
        return surfaceComponents[surfaceId]?.get(componentId)
    }

    fun getSurfaceContext(surfaceId: String): SurfaceContext? {
        return surfaces[surfaceId]
    }

    fun getSurfaceContextFlow(surfaceId: String): Flow<SurfaceContext?> {
        val changeFlow = surfaceChanges[surfaceId] ?: MutableStateFlow(Unit)
        
        return changeFlow.map {
            surfaces[surfaceId]
        }
    }

    fun getComponentFlow(surfaceId: String, componentId: String): Flow<Component?> {
        val changeFlow = surfaceChanges[surfaceId] ?: MutableStateFlow(Unit)
        
        return changeFlow.map {
            surfaceComponents[surfaceId]?.get(componentId)
        }
    }

    fun handleAction(surfaceId: String, action: Action) {
        logger.log(A2UILogLevel.INFO, "Handling action on surface: $surfaceId")
        when {
            action.event != null -> {
                logger.log(A2UILogLevel.DEBUG, "Action event: ${action.event.name}")
                _actionHandler.value?.onAction(surfaceId, action.event.name, action.event.context ?: emptyMap())
            }
            action.functionCall != null -> {
                logger.log(A2UILogLevel.DEBUG, "Action function: ${action.functionCall.call}")
                handleLocalFunction(action.functionCall)
            }
        }
    }

    private fun handleLocalFunction(functionCall: FunctionCall) {
        when (functionCall.call) {
            "openUrl" -> {
                val url = functionCall.args["url"] as? String
                if (url != null && ALLOWED_URL_SCHEMES.any { url.startsWith(it) }) {
                    _actionHandler.value?.openUrl(url)
                } else {
                    logger.log(A2UILogLevel.WARN, "Blocked unsafe URL scheme: $url")
                }
            }
            "showToast" -> {
                val message = functionCall.args["message"] as? String
                if (message != null) {
                    _actionHandler.value?.showToast(message)
                }
            }
        }
    }

    @Composable
    fun renderSurface(surfaceId: String): @Composable () -> Unit {
        val context = surfaces[surfaceId]
        val rootComponent = surfaceComponents[surfaceId]?.get("root")

        return {
            if (context != null && rootComponent != null) {
                registry.render(rootComponent, context)
            } else {
                androidx.compose.material3.CircularProgressIndicator()
            }
        }
    }

    fun getAllSurfaceIds(): List<String> {
        return surfaces.keys.toList()
    }

    fun getSurfaceState(surfaceId: String): A2UIRendererState? {
        return surfaceStates[surfaceId]
    }

    fun getDataModel(surfaceId: String): DataModelState? {
        return dataModelProcessor.getDataModel(surfaceId)
    }

    fun saveState(): SavedRendererState {
        val dataModels = mutableMapOf<String, Map<String, Any?>>()
        surfaces.keys.forEach { surfaceId ->
            dataModels[surfaceId] = dataModelProcessor.getDataModel(surfaceId)?.getDataSnapshot() ?: emptyMap()
        }

        val components = mutableMapOf<String, Map<String, Component>>()
        surfaceComponents.forEach { (surfaceId, componentMap) ->
            components[surfaceId] = componentMap.toMap()
        }

        return SavedRendererState(
            surfaces = surfaces.toMap(),
            dataModels = dataModels,
            components = components
        )
    }

    fun restoreState(savedState: SavedRendererState) {
        // ✅ 先备份当前状态，失败时回滚
        val backupSurfaces = surfaces.toMap()
        val backupComponents = surfaceComponents.mapValues { (_, v) -> v.toMap() }
        val backupStates = surfaceStates.toMap()

        try {
            dispose()

            savedState.surfaces.forEach { (surfaceId, context) ->
                surfaces[surfaceId] = context
                surfaceComponents[surfaceId] = mutableStateMapOf()
                surfaceStates[surfaceId] = A2UIRendererState.Idle
                surfaceChanges[surfaceId] = MutableStateFlow(Unit)
                dataModelProcessor.createSurface(surfaceId)
            }

            savedState.dataModels.forEach { (surfaceId, data) ->
                dataModelProcessor.updateDataModel(surfaceId, "/", data)
            }

            savedState.components.forEach { (surfaceId, componentMap) ->
                val componentStateMap = surfaceComponents[surfaceId] ?: return@forEach
                componentMap.forEach { (componentId, component) ->
                    componentStateMap[componentId] = component
                }
            }

            logger.log(A2UILogLevel.INFO, "Restored state for ${savedState.surfaces.size} surfaces")
        } catch (e: Exception) {
            // ✅ 回滚
            logger.log(A2UILogLevel.ERROR, "Failed to restore state, rolling back: ${e.message}")
            dispose()

            backupSurfaces.forEach { (surfaceId, context) ->
                surfaces[surfaceId] = context
                surfaceComponents[surfaceId] = mutableStateMapOf()
                surfaceStates[surfaceId] = backupStates[surfaceId] ?: A2UIRendererState.Idle
                surfaceChanges[surfaceId] = MutableStateFlow(Unit)
                dataModelProcessor.createSurface(surfaceId)
            }
            backupComponents.forEach { (surfaceId, componentMap) ->
                val stateMap = surfaceComponents[surfaceId] ?: return@forEach
                componentMap.forEach { (id, comp) -> stateMap[id] = comp }
            }

            throw e
        }
    }

    fun dispose() {
        surfaces.clear()
        surfaceComponents.clear()
        surfaceStates.clear()
        surfaceChanges.clear()
        dataModelProcessor.clear()
    }
}

data class SurfaceContext(
    val surfaceId: String,
    val catalogId: String,
    val theme: Theme? = null,
    val sendDataModel: Boolean = false,
    val renderDepth: Int = 0,
    /** Collection scope path for template rendering (e.g., "/users/0") */
    val scopePath: String? = null
)

interface ActionHandler {
    fun onAction(surfaceId: String, actionName: String, context: Map<String, Any>)
    fun openUrl(url: String)
    fun showToast(message: String)
}

enum class A2UILogLevel {
    DEBUG, INFO, WARN, ERROR
}

interface A2UILogger {
    fun log(level: A2UILogLevel, message: String)
}

class DefaultLogger : A2UILogger {
    override fun log(level: A2UILogLevel, message: String) {
        when (level) {
            A2UILogLevel.DEBUG -> println("[A2UI DEBUG] $message")
            A2UILogLevel.INFO -> println("[A2UI INFO] $message")
            A2UILogLevel.WARN -> println("[A2UI WARN] $message")
            A2UILogLevel.ERROR -> println("[A2UI ERROR] $message")
        }
    }
}

@Composable
fun rememberA2UIRenderer(
    logger: A2UILogger = DefaultLogger()
): A2UIRenderer {
    return rememberSaveable(saver = A2UIRenderer.Saver) {
        A2UIRenderer(logger)
    }
}

/**
 * Serializer for Any type — handles JSON primitives, objects, and arrays
 */
object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(anyToJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as JsonDecoder
        return jsonElementToAny(jsonDecoder.decodeJsonElement())
    }

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) })
        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun jsonElementToAny(element: JsonElement): Any = when (element) {
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonObject -> element.mapValues { jsonElementToAny(it.value) }
        is JsonArray -> element.map { jsonElementToAny(it) }
        else -> element.toString()
    }
}
