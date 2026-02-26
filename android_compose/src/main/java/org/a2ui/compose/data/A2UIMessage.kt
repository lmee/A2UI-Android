package org.a2ui.compose.data

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
sealed class A2UIMessage {
    abstract val version: String

    companion object {
        val SUPPORTED_VERSIONS = setOf("v0.8", "v0.9", "v0.10")
        private val KNOWN_VERSION_REGEX = """^v\d+\.\d+$""".toRegex()

        fun isVersionSupported(version: String): Boolean = version in SUPPORTED_VERSIONS
        fun isVersionKnown(version: String): Boolean = KNOWN_VERSION_REGEX.matches(version)
    }
}

object A2UIProtocol {
    private val VALID_ID_REGEX = """^[a-zA-Z0-9_.\-]{1,128}$""".toRegex()

    fun isValidId(id: String): Boolean = VALID_ID_REGEX.matches(id)
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
    @Contextual val value: Any? = null
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

/**
 * Custom serializer for ChildList: handles both plain arrays and object formats
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
object ChildListSerializer : KSerializer<ChildList> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("ChildList", StructureKind.OBJECT)

    override fun serialize(encoder: Encoder, value: ChildList) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is ChildList.ArrayChildList -> jsonEncoder.encodeJsonElement(
                buildJsonObject { put("array", JsonArray(value.array.map { JsonPrimitive(it) })) }
            )
            is ChildList.ObjectChildList -> jsonEncoder.encodeJsonElement(
                buildJsonObject {
                    putJsonObject("objectChild") {
                        put("path", value.objectChild.path)
                        put("componentId", value.objectChild.componentId)
                    }
                }
            )
        }
    }

    override fun deserialize(decoder: Decoder): ChildList {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonArray -> ChildList.ArrayChildList(element.map { it.jsonPrimitive.content })
            element is JsonObject && "array" in element ->
                ChildList.ArrayChildList(element["array"]!!.jsonArray.map { it.jsonPrimitive.content })
            element is JsonObject && "objectChild" in element -> {
                val obj = element["objectChild"]!!.jsonObject
                ChildList.ObjectChildList(ChildTemplate(
                    path = obj["path"]!!.jsonPrimitive.content,
                    componentId = obj["componentId"]!!.jsonPrimitive.content
                ))
            }
            element is JsonObject && "path" in element && "componentId" in element ->
                ChildList.ObjectChildList(ChildTemplate(
                    path = element["path"]!!.jsonPrimitive.content,
                    componentId = element["componentId"]!!.jsonPrimitive.content
                ))
            else -> ChildList.ArrayChildList(emptyList())
        }
    }
}

/**
 * Custom serializer for DynamicValue: handles plain primitives, {"path":"..."}, {"literal":"..."}, {"functionCall":{...}}
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class DynamicValueSerializer<T>(private val tSerializer: KSerializer<T>) : KSerializer<DynamicValue<T>> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("DynamicValue", StructureKind.OBJECT)

    override fun serialize(encoder: Encoder, value: DynamicValue<T>) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is DynamicValue.LiteralValue -> jsonEncoder.encodeJsonElement(
                buildJsonObject { put("literal", JsonPrimitive(value.literal.toString())) }
            )
            is DynamicValue.PathValue -> jsonEncoder.encodeJsonElement(
                buildJsonObject { put("path", JsonPrimitive(value.path)) }
            )
            is DynamicValue.FunctionValue -> jsonEncoder.encodeJsonElement(
                buildJsonObject {
                    putJsonObject("functionCall") {
                        put("call", JsonPrimitive(value.functionCall.call))
                        put("returnType", value.functionCall.returnType?.let { JsonPrimitive(it) } ?: JsonNull)
                    }
                }
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): DynamicValue<T> {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonPrimitive -> {
                val value: Any = when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
                DynamicValue.LiteralValue(value as T)
            }
            element is JsonObject && "path" in element ->
                DynamicValue.PathValue(element["path"]!!.jsonPrimitive.content)
            element is JsonObject && "literal" in element -> {
                val lit = element["literal"]!!
                val value: Any = when {
                    lit is JsonPrimitive && lit.isString -> lit.content
                    lit is JsonPrimitive && lit.booleanOrNull != null -> lit.boolean
                    lit is JsonPrimitive && lit.longOrNull != null -> lit.long
                    lit is JsonPrimitive && lit.doubleOrNull != null -> lit.double
                    lit is JsonPrimitive -> lit.content
                    else -> lit.toString()
                }
                DynamicValue.LiteralValue(value as T)
            }
            element is JsonObject && "functionCall" in element -> {
                val fc = element["functionCall"]!!.jsonObject
                DynamicValue.FunctionValue(FunctionCall(
                    call = fc["call"]!!.jsonPrimitive.content,
                    args = fc["args"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
                    returnType = fc["returnType"]?.jsonPrimitive?.contentOrNull
                ))
            }
            else -> DynamicValue.LiteralValue(element.toString() as T)
        }
    }
}

// Concrete serializer instances
object StringDynamicValueSerializer : KSerializer<DynamicValue<String>> by DynamicValueSerializer(String.serializer())
object AnyDynamicValueSerializer : KSerializer<DynamicValue<Any>> by DynamicValueSerializer(ContextualSerializer(Any::class))

@Serializable
class Component(
    val id: String,
    val component: String,
    val text: @Serializable(with = StringDynamicValueSerializer::class) DynamicValue<String>? = null,
    val url: @Serializable(with = StringDynamicValueSerializer::class) DynamicValue<String>? = null,
    val children: @Serializable(with = ChildListSerializer::class) ChildList? = null,
    val child: String? = null,
    val action: Action? = null,
    val value: @Serializable(with = AnyDynamicValueSerializer::class) DynamicValue<@Contextual Any>? = null,
    val label: @Serializable(with = StringDynamicValueSerializer::class) DynamicValue<String>? = null,
    val variant: String? = null,
    val weight: Int? = null,
    val checks: List<Check>? = null,
    val required: Boolean? = null,
    val pattern: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val validationRegexp: String? = null,
    val justify: String? = null,
    val align: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<Option>? = null,
    val multiple: Boolean? = null,
    val displayStyle: String? = null,
    val filterable: Boolean? = null,
    val placeholder: @Serializable(with = StringDynamicValueSerializer::class) DynamicValue<String>? = null,
    val name: @Serializable(with = StringDynamicValueSerializer::class) DynamicValue<String>? = null,
    val fit: String? = null,
    val trigger: String? = null,
    val content: String? = null,
    val entryPointChild: String? = null,
    val contentChild: String? = null,
    val tabs: List<TabItem>? = null,
    val tabItems: List<TabItem>? = null,
    val enableDate: Boolean? = null,
    val enableTime: Boolean? = null,
    val direction: String? = null,
    val axis: String? = null,
    val primary: Boolean? = null,
    val usageHint: String? = null,
    val selections: @Serializable(with = AnyDynamicValueSerializer::class) DynamicValue<@Contextual Any>? = null,
    val maxAllowedSelections: Int? = null,
    val textFieldType: String? = null,
    val description: @Serializable(with = StringDynamicValueSerializer::class) DynamicValue<String>? = null
)

@Serializable
class TabItem(
    val title: @Serializable(with = StringDynamicValueSerializer::class) DynamicValue<String>? = null,
    val child: String? = null
)

@Serializable(with = ChildListSerializer::class)
sealed class ChildList {
    data class ArrayChildList(val array: List<String>) : ChildList()
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
    val context: Map<String, @Contextual Any>? = null
)

@Serializable
class FunctionCall(
    val call: String,
    val args: Map<String, @Contextual Any>,
    val returnType: String? = null
)

@Serializable
class Check(
    val call: String,
    val args: Map<String, @Contextual Any>,
    val message: String? = null,
    val condition: FunctionCall? = null
)

@Serializable
class Option(
    val label: String,
    @Contextual val value: Any
)

sealed class DynamicValue<T> {
    data class LiteralValue<T>(val literal: T) : DynamicValue<T>()
    data class PathValue<T>(val path: String) : DynamicValue<T>()
    data class FunctionValue<T>(val functionCall: FunctionCall) : DynamicValue<T>()
}
