package org.a2ui.compose.rendering

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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
    private val componentRegistry = ComponentRegistry(this)
    private val surfaces = mutableStateMapOf<String, SurfaceContext>()
    private val surfaceComponents = mutableStateMapOf<String, SnapshotStateMap<String, Component>>()
    private val surfaceStates = mutableStateMapOf<String, A2UIRendererState>()

    private val _actionHandler = MutableStateFlow<ActionHandler?>(null)
    val actionHandler: StateFlow<ActionHandler?>
        get() = _actionHandler.asStateFlow()

    private val _errors = mutableStateListOf<A2UIError>()
    val errors: List<A2UIError> get() = _errors.toList()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
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

            val surfaceId = getSurfaceId(message)
            val a2uiMessage = try {
                json.decodeFromString<A2UIMessage>(message)
            } catch (e: SerializationException) {
                val error = A2UIError.ParseError("Invalid JSON format: ${e.message}", message)
                handleError(error, surfaceId)
                return Result.failure(e)
            }
            
            when (a2uiMessage) {
                is CreateSurfaceMessage -> handleCreateSurface(a2uiMessage.createSurface)
                is UpdateComponentsMessage -> handleUpdateComponents(a2uiMessage.updateComponents)
                is UpdateDataModelMessage -> handleUpdateDataModel(a2uiMessage.updateDataModel)
                is DeleteSurfaceMessage -> handleDeleteSurface(a2uiMessage.deleteSurface)
            }
            
            surfaceStates[surfaceId] = A2UIRendererState.Idle
            surfaceChanges[surfaceId]?.value = Unit
            
            logger.log(A2UILogLevel.DEBUG, "Message processed successfully for surface: $surfaceId")
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            val surfaceId = getSurfaceId(message)
            val error = A2UIError.StateError(surfaceId, "Invalid argument: ${e.message}")
            handleError(error, surfaceId)
            logger.log(A2UILogLevel.ERROR, "Invalid argument: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            val surfaceId = getSurfaceId(message)
            val error = A2UIError.UnknownError("Unexpected error: ${e.message}", e)
            handleError(error, surfaceId)
            logger.log(A2UILogLevel.ERROR, "Error processing message: ${e.message}")
            Result.failure(e)
        }
    }

    private fun handleError(error: A2UIError, surfaceId: String) {
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
        val surfaceId = createSurface.surfaceId
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

        components.forEach { component ->
            componentMap[component.id] = component
        }
        
        surfaceChanges[surfaceId]?.value = Unit
        logger.log(A2UILogLevel.DEBUG, "Updated ${components.size} components in surface: $surfaceId")
    }

    private fun handleUpdateDataModel(updateDataModel: UpdateDataModel) {
        logger.log(A2UILogLevel.DEBUG, "Updating data model: ${updateDataModel.surfaceId} at path ${updateDataModel.path}")
        dataModelProcessor.updateDataModel(
            updateDataModel.surfaceId,
            updateDataModel.path,
            updateDataModel.value
        )
        
        surfaceChanges[updateDataModel.surfaceId]?.value = Unit
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
                if (url != null) {
                    _actionHandler.value?.openUrl(url)
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
                componentRegistry.render(rootComponent, context)
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
    }

    fun dispose() {
        surfaces.clear()
        surfaceComponents.clear()
        surfaceStates.clear()
        surfaceChanges.clear()
        dataModelProcessor.clear()
    }

    companion object {
        val Saver: Saver<A2UIRenderer, SavedRendererState> = Saver(
            save = { renderer -> renderer.saveState() },
            restore = { savedState ->
                A2UIRenderer().apply { restoreState(savedState) }
            }
        )
    }
}

data class SurfaceContext(
    val surfaceId: String,
    val catalogId: String,
    val theme: Theme? = null,
    val sendDataModel: Boolean = false
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
