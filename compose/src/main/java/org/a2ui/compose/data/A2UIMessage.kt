package org.a2ui.compose.data

import kotlinx.serialization.Serializable

@Serializable
sealed class A2UIMessage {
    abstract val version: String
}

@Serializable
data class CreateSurfaceMessage(
    override val version: String,
    val createSurface: CreateSurface
) : A2UIMessage()

@Serializable
class UpdateComponentsMessage(
    override val version: String,
    val updateComponents: UpdateComponents
) : A2UIMessage()

@Serializable
class UpdateDataModelMessage(
    override val version: String,
    val updateDataModel: UpdateDataModel
) : A2UIMessage()

@Serializable
class DeleteSurfaceMessage(
    override val version: String,
    val deleteSurface: DeleteSurface
) : A2UIMessage()

@Serializable
class CreateSurface(
    val surfaceId: String,
    val catalogId: String,
    val theme: Theme? = null,
    val sendDataModel: Boolean = false
)

@Serializable
class UpdateComponents(
    val surfaceId: String,
    val components: List<Component>
)

@Serializable
class UpdateDataModel(
    val surfaceId: String,
    val path: String = "/",
    val value: Any? = null
)

@Serializable
class DeleteSurface(
    val surfaceId: String
)

@Serializable
class Theme(
    val primaryColor: String? = null,
    val iconUrl: String? = null,
    val agentDisplayName: String? = null
)

@Serializable
class Component(
    val id: String,
    val component: String,
    val text: DynamicValue<String>? = null,
    val url: DynamicValue<String>? = null,
    val children: ChildList? = null,
    val child: String? = null,
    val action: Action? = null,
    val value: DynamicValue<Any>? = null,
    val label: DynamicValue<String>? = null,
    val variant: String? = null,
    val checks: List<Check>? = null,
    val justify: String? = null,
    val align: String? = null,
    val weight: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<Option>? = null,
    val multiple: Boolean? = null,
    val placeholder: DynamicValue<String>? = null,
    val required: Boolean? = null,
    val pattern: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null
)

@Serializable
sealed class ChildList {
    @Serializable
    data class ArrayChildList(val array: List<String>) : ChildList()
    @Serializable
    data class ObjectChildList(val objectChild: ChildTemplate) : ChildList()
}

@Serializable
class ChildTemplate(
    val path: String,
    val componentId: String
)

@Serializable
class Action(
    val event: Event? = null,
    val functionCall: FunctionCall? = null
)

@Serializable
class Event(
    val name: String,
    val context: Map<String, Any>? = null
)

@Serializable
class FunctionCall(
    val call: String,
    val args: Map<String, Any>
)

@Serializable
class Check(
    val call: String,
    val args: Map<String, Any>,
    val message: String? = null,
    val condition: FunctionCall? = null
)

@Serializable
class Option(
    val label: String,
    val value: Any
)

@Serializable
sealed class DynamicValue<T> {
    @Serializable
    data class LiteralValue<T>(val literal: T) : DynamicValue<T>()
    @Serializable
    data class PathValue<T>(val path: String) : DynamicValue<T>()
    @Serializable
    data class FunctionValue<T>(val functionCall: FunctionCall) : DynamicValue<T>()
}
