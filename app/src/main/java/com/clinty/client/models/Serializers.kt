package com.clinty.client.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

sealed class HumanResponseArgs {
    data object NullArg : HumanResponseArgs()
    data class Text(val value: String) : HumanResponseArgs()
    data class Action(val request: ActionRequest) : HumanResponseArgs()
}

object HumanResponseArgsSerializer : KSerializer<HumanResponseArgs> {
    override val descriptor: SerialDescriptor =
        JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): HumanResponseArgs {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonNull -> HumanResponseArgs.NullArg
            element is JsonPrimitive && element.isString ->
                HumanResponseArgs.Text(element.content)
            element is JsonPrimitive -> HumanResponseArgs.Text(element.content)
            else -> HumanResponseArgs.Action(
                AppJson.decodeFromElement(ActionRequest.serializer(), element),
            )
        }
    }

    override fun serialize(encoder: Encoder, value: HumanResponseArgs) {
        val jsonEncoder = encoder as JsonEncoder
        val element = when (value) {
            is HumanResponseArgs.NullArg -> JsonNull
            is HumanResponseArgs.Text -> JsonPrimitive(value.value)
            is HumanResponseArgs.Action ->
                AppJson.encodeToElement(ActionRequest.serializer(), value.request)
        }
        jsonEncoder.encodeJsonElement(element)
    }
}

object JsonValueMapSerializer : KSerializer<Map<String, JsonValue>> {
    override val descriptor: SerialDescriptor =
        JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): Map<String, JsonValue> {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return element.jsonObject.mapValues { (_, v) -> JsonValue.fromJsonElement(v) }
    }

    override fun serialize(encoder: Encoder, value: Map<String, JsonValue>) {
        val jsonEncoder = encoder as JsonEncoder
        val obj = kotlinx.serialization.json.buildJsonObject {
            value.forEach { (key, v) -> put(key, v.toJsonElement()) }
        }
        jsonEncoder.encodeJsonElement(obj)
    }
}

object JsonValueSerializer : KSerializer<JsonValue> {
    override val descriptor: SerialDescriptor =
        JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): JsonValue {
        val jsonDecoder = decoder as JsonDecoder
        return JsonValue.fromJsonElement(jsonDecoder.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: JsonValue) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(value.toJsonElement())
    }
}
