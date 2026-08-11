package com.clinty.client.models

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.format.DateTimeFormatter

@Serializable
data class LangGraphThreadSummary(
    @SerialName("thread_id") val threadId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("state_updated_at") val stateUpdatedAt: String? = null,
    @Serializable(with = JsonValueMapSerializer::class)
    val metadata: Map<String, JsonValue>? = null,
    @Serializable(with = JsonValueSerializer::class)
    val config: JsonValue? = null,
    @Serializable(with = JsonValueSerializer::class)
    val error: JsonValue? = null,
    @Serializable(with = JsonValueSerializer::class)
    val values: JsonValue? = null,
    @Serializable(with = JsonValueMapSerializer::class)
    val interrupts: Map<String, JsonValue>? = null,
) {
    val id: String get() = threadId

    val createdDate: Instant?
        get() = parseThreadInstant(createdAt)

    val updatedDate: Instant?
        get() = parseThreadInstant(updatedAt)

    val messageDate: Instant?
        get() = createdDate ?: updatedDate

    fun messageSubject(): String? {
        val emailInput = values?.objectValue?.get("email_input")?.objectValue ?: return null
        return emailInput["subject"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
    }
}

@Serializable
data class ThreadSearchRequest(
    val offset: Int,
    val limit: Int,
    val status: String? = null,
    val metadata: ThreadSearchMetadata? = null,
) {
    constructor(offset: Int, limit: Int, graphId: String) : this(
        offset = offset,
        limit = limit,
        status = ThreadStatusFilter.INTERRUPTED.apiValue,
        metadata = ThreadSearchMetadata(graphId),
    )
}

@Serializable
data class ThreadSearchMetadata(
    @SerialName("graph_id") val graphId: String? = null,
    @SerialName("assistant_id") val assistantId: String? = null,
) {
    constructor(graphId: String) : this(
        graphId = graphId.takeUnless { isUuid(graphId) },
        assistantId = graphId.takeIf { isUuid(graphId) },
    )

    companion object {
        private fun isUuid(value: String): Boolean = runCatching {
            java.util.UUID.fromString(value)
            true
        }.getOrDefault(false)
    }
}

data class ThreadData(
    val thread: LangGraphThreadSummary,
    val status: String,
    val interrupts: List<HumanInterrupt>?,
    val invalidSchema: Boolean,
) {
    val id: String get() = thread.threadId

    fun inboxSubject(): String {
        thread.messageSubject()?.let { return it }
        return interrupts?.firstOrNull()?.displayTitle() ?: "Thread"
    }

    fun inboxDate(): Instant? = thread.messageDate
}

@Serializable
data class ThreadStateResponse(
    @Serializable(with = JsonValueSerializer::class)
    val values: JsonValue? = null,
    val tasks: List<ThreadTask>? = null,
)

@Serializable
data class ThreadTask(
    val interrupts: List<ThreadTaskInterrupt>? = null,
)

@Serializable
data class ThreadTaskInterrupt(
    @Serializable(with = JsonValueSerializer::class)
    val value: JsonValue? = null,
)

@Serializable
data class UpdateThreadStateRequest(
    @Serializable(with = JsonValueSerializer::class)
    val values: JsonValue? = null,
    @SerialName("as_node") val asNode: String,
) {
    companion object {
        val resolve = UpdateThreadStateRequest(values = null, asNode = "__end__")
    }
}

@Serializable
data class RunResumeRequest(
    @SerialName("assistant_id") val assistantId: String,
    val command: RunCommand,
    @SerialName("stream_mode") val streamMode: String,
)

@Serializable
data class RunCommand(
    val resume: List<HumanResponse>,
)

@Serializable
data class DeploymentInfoResponse(
    val host: DeploymentHost? = null,
)

@Serializable
data class DeploymentHost(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("tenant_id") val tenantId: String? = null,
)

@Serializable(with = JsonValueSerializer::class)
sealed class JsonValue {
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data class Obj(val value: Map<String, JsonValue>) : JsonValue()
    data class Arr(val value: List<JsonValue>) : JsonValue()
    data object Null : JsonValue()

    val stringValue: String?
        get() = (this as? Str)?.value

    val objectValue: Map<String, JsonValue>?
        get() = (this as? Obj)?.value

    val arrayValue: List<JsonValue>?
        get() = (this as? Arr)?.value

    fun prettyPrinted(): String? {
        val element = toJsonElement()
        return runCatching {
            Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), element)
        }.getOrNull()
    }

    fun toJsonElement(): JsonElement = when (this) {
        is Str -> JsonPrimitive(value)
        is Num -> JsonPrimitive(value)
        is Bool -> JsonPrimitive(value)
        is Obj -> JsonObject(value.mapValues { (_, v) -> v.toJsonElement() })
        is Arr -> kotlinx.serialization.json.JsonArray(value.map { it.toJsonElement() })
        Null -> JsonNull
    }

    companion object {
        fun fromJsonElement(element: JsonElement): JsonValue = when {
            element is JsonNull -> Null
            element is JsonObject -> Obj(element.mapValues { (_, v) -> fromJsonElement(v) })
            element is kotlinx.serialization.json.JsonArray ->
                Arr(element.map { fromJsonElement(it) })
            element is JsonPrimitive -> when {
                element.isString -> Str(element.content)
                element.content == "true" || element.content == "false" ->
                    Bool(element.content.toBoolean())
                else -> element.content.toDoubleOrNull()?.let { Num(it) } ?: Str(element.content)
            }
            else -> Null
        }
    }
}

object AppJson {
    val instance = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    inline fun <reified T> encodeToString(value: T): String = instance.encodeToString(value)

    inline fun <reified T> decodeFromString(string: String): T = instance.decodeFromString(string)

    fun <T> decodeFromElement(deserializer: DeserializationStrategy<T>, element: JsonElement): T =
        instance.decodeFromJsonElement(deserializer, element)

    fun <T> encodeToElement(serializer: SerializationStrategy<T>, value: T): JsonElement =
        instance.encodeToJsonElement(serializer, value)
}

fun jsonValueString(value: JsonValue?): String {
    if (value == null) return ""
    return when (value) {
        is JsonValue.Str -> value.value
        is JsonValue.Bool -> if (value.value) "true" else "false"
        is JsonValue.Num -> value.value.toString()
        is JsonValue.Null -> ""
        is JsonValue.Obj, is JsonValue.Arr ->
            runCatching {
                AppJson.encodeToString(value.toJsonElement())
            }.getOrDefault("")
    }
}

fun formatInstant(instant: Instant?): String {
    if (instant == null) return ""
    return DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
        .withZone(java.time.ZoneId.systemDefault())
        .format(instant)
}

fun parseThreadInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        java.time.OffsetDateTime.parse(raw).toInstant()
    }.recoverCatching {
        Instant.parse(raw)
    }.getOrNull()
}

private val subjectLineRegex = Regex("""\*\*Subject\*\*:\s*(.+?)(?:\r?\n|$)""")

fun HumanInterrupt.subjectFromDescription(): String? {
    val description = description ?: return null
    return subjectLineRegex.find(description)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

fun currentIsoTimestamp(): String = Instant.now().toString()
