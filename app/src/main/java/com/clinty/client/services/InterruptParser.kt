package com.clinty.client.services

import com.clinty.client.models.ActionRequest
import com.clinty.client.models.AgentInboxConstants
import com.clinty.client.models.AppJson
import com.clinty.client.models.HumanInterrupt
import com.clinty.client.models.HumanInterruptConfig
import com.clinty.client.models.JsonValue
import com.clinty.client.models.LangGraphThreadSummary
import com.clinty.client.models.ThreadData
import com.clinty.client.models.ThreadStateResponse
import kotlinx.serialization.json.JsonElement

object InterruptParser {
    fun interruptsFromThread(thread: LangGraphThreadSummary): List<HumanInterrupt>? {
        val raw = thread.interrupts ?: return null
        if (raw.isEmpty()) return null
        val flattened = raw.values.flatMap { parseInterruptEntry(it) }
        return flattened.ifEmpty { null }
    }

    fun processInterruptedThread(thread: LangGraphThreadSummary): ThreadData? {
        if (thread.status != "interrupted") return null
        val parsed = interruptsFromThread(thread)
        val invalid = parsed?.any {
            it.actionRequest.action == AgentInboxConstants.IMPROPER_SCHEMA
        } ?: (parsed == null)

        return ThreadData(
            thread = thread,
            status = "interrupted",
            interrupts = parsed,
            invalidSchema = invalid || parsed == null,
        )
    }

    fun interruptsFromState(state: ThreadStateResponse): List<HumanInterrupt>? {
        val tasks = state.tasks ?: return null
        val lastTask = tasks.lastOrNull() ?: return null
        val interrupts = lastTask.interrupts ?: return null
        val last = interrupts.lastOrNull() ?: return null
        val value = last.value ?: return null

        normalizeHitlInterrupt(value)?.let { return listOf(it) }
        return decodeInterrupts(value)
    }

    private fun parseInterruptEntry(value: JsonValue): List<HumanInterrupt> {
        if (value !is JsonValue.Arr) {
            decodeInterrupt(value)?.let { return listOf(it) }
            return listOf(improperSchemaInterrupt())
        }

        val results = mutableListOf<HumanInterrupt>()
        for (item in value.value) {
            val nested = nestedInterrupt(item)
            if (nested != null) {
                results.add(nested)
                continue
            }
            if (item is JsonValue.Obj) {
                val inner = item.value["value"]
                if (inner != null) {
                    val normalized = normalizeHitlInterrupt(inner)
                    if (normalized != null) {
                        results.add(normalized)
                        continue
                    }
                    val decoded = decodeInterrupt(inner)
                    if (decoded != null) {
                        results.add(decoded)
                        continue
                    }
                    val array = inner.arrayValue
                    if (array != null) {
                        results.addAll(array.mapNotNull { decodeInterrupt(it) })
                        continue
                    }
                }
            }
            decodeInterrupt(item)?.let { results.add(it) } ?: results.add(improperSchemaInterrupt())
        }
        return results.ifEmpty { listOf(improperSchemaInterrupt()) }
    }

    private fun nestedInterrupt(item: JsonValue): HumanInterrupt? {
        if (item !is JsonValue.Arr) return null
        val first = item.value.firstOrNull() ?: return null
        if (first !is JsonValue.Arr) return null
        if (first.value.size <= 1) return null
        val value = (first.value[1] as? JsonValue.Obj)?.value?.get("value") ?: return null
        return decodeInterrupt(value) ?: normalizeHitlInterrupt(value)
    }

    private fun decodeInterrupts(value: JsonValue): List<HumanInterrupt>? {
        decodeInterrupt(value)?.let { return listOf(it) }
        value.arrayValue?.let { array ->
            val decoded = array.mapNotNull { decodeInterrupt(it) }
            return decoded.ifEmpty { null }
        }
        value.stringValue?.let { string ->
            return runCatching {
                AppJson.decodeFromString<List<HumanInterrupt>>(string)
            }.getOrNull()
        }
        return runCatching {
            val element: JsonElement = value.toJsonElement()
            listOf(AppJson.decodeFromElement(HumanInterrupt.serializer(), element))
        }.getOrNull()
    }

    private fun decodeInterrupt(value: JsonValue): HumanInterrupt? {
        return runCatching {
            AppJson.decodeFromElement(
                HumanInterrupt.serializer(),
                value.toJsonElement(),
            )
        }.getOrNull()
    }

    private fun normalizeHitlInterrupt(value: JsonValue): HumanInterrupt? {
        val objectValue = value.objectValue ?: return null
        val requests = (objectValue["action_requests"] as? JsonValue.Arr)?.value ?: return null
        val first = (requests.firstOrNull() as? JsonValue.Obj)?.value ?: return null

        val action = (first["name"] as? JsonValue.Str)?.value.orEmpty()
        val args = (first["arguments"] as? JsonValue.Obj)?.value ?: emptyMap()
        val description = (first["description"] as? JsonValue.Str)?.value

        val decisions = mutableListOf<String>()
        val configs = (objectValue["review_configs"] as? JsonValue.Arr)?.value
        val config = (configs?.firstOrNull() as? JsonValue.Obj)?.value
        val allowed = (config?.get("allowed_decisions") as? JsonValue.Arr)?.value
        allowed?.forEach { decision ->
            (decision as? JsonValue.Str)?.value?.let { decisions.add(it) }
        }

        return HumanInterrupt(
            actionRequest = ActionRequest(action = action, args = args),
            config = HumanInterruptConfig(
                allowIgnore = false,
                allowRespond = decisions.contains("reject"),
                allowEdit = decisions.contains("edit"),
                allowAccept = decisions.contains("approve"),
            ),
            description = description,
        )
    }

    private fun improperSchemaInterrupt(): HumanInterrupt {
        return HumanInterrupt(
            actionRequest = ActionRequest(
                action = AgentInboxConstants.IMPROPER_SCHEMA,
                args = emptyMap(),
            ),
            config = HumanInterruptConfig(
                allowIgnore = true,
                allowRespond = false,
                allowEdit = false,
                allowAccept = false,
            ),
            description = null,
        )
    }
}
