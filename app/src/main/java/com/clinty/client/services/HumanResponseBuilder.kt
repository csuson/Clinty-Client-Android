package com.clinty.client.services

import com.clinty.client.models.ActionRequest
import com.clinty.client.models.AppJson
import com.clinty.client.models.HumanInterrupt
import com.clinty.client.models.HumanResponse
import com.clinty.client.models.HumanResponseArgs
import com.clinty.client.models.HumanResponseType
import com.clinty.client.models.JsonValue
import com.clinty.client.models.SubmitType
import java.util.UUID

object HumanResponseBuilder {
    data class DefaultResponsesResult(
        val responses: List<PendingHumanResponse>,
        val defaultSubmit: SubmitType?,
    )

    fun defaultResponses(interrupt: HumanInterrupt): DefaultResponsesResult {
        val responses = mutableListOf<PendingHumanResponse>()
        var editArgs = interrupt.actionRequest

        if (interrupt.config.allowEdit) {
            editArgs = stringifiedArgs(interrupt.actionRequest)
            responses.add(
                PendingHumanResponse(
                    type = HumanResponseType.EDIT,
                    args = PendingArgs.Action(editArgs),
                    acceptAllowed = interrupt.config.allowAccept,
                    editsMade = false,
                ),
            )
        }

        if (interrupt.config.allowRespond) {
            responses.add(
                PendingHumanResponse(
                    type = HumanResponseType.RESPONSE,
                    args = PendingArgs.Text(""),
                    acceptAllowed = false,
                    editsMade = false,
                ),
            )
        }

        if (interrupt.config.allowIgnore) {
            responses.add(
                PendingHumanResponse(
                    type = HumanResponseType.IGNORE,
                    args = PendingArgs.NullArg,
                    acceptAllowed = false,
                    editsMade = false,
                ),
            )
        }

        if (interrupt.config.allowAccept &&
            responses.none { it.type == HumanResponseType.ACCEPT }
        ) {
            responses.add(
                PendingHumanResponse(
                    type = HumanResponseType.ACCEPT,
                    args = PendingArgs.Action(stringifiedArgs(interrupt.actionRequest)),
                    acceptAllowed = true,
                    editsMade = false,
                ),
            )
        }

        val defaultSubmit = when {
            interrupt.config.allowAccept || responses.any { it.acceptAllowed } -> SubmitType.ACCEPT
            interrupt.config.allowRespond -> SubmitType.RESPONSE
            interrupt.config.allowEdit -> SubmitType.EDIT
            else -> null
        }

        return DefaultResponsesResult(responses, defaultSubmit)
    }

    fun buildSubmission(
        pending: PendingHumanResponse,
        responseText: String,
        editedArgs: Map<String, String>,
    ): HumanResponse {
        return when (pending.type) {
            HumanResponseType.IGNORE -> HumanResponse(
                type = HumanResponseType.IGNORE,
                args = HumanResponseArgs.NullArg,
            )
            HumanResponseType.RESPONSE -> HumanResponse(
                type = HumanResponseType.RESPONSE,
                args = HumanResponseArgs.Text(responseText),
            )
            HumanResponseType.ACCEPT -> {
                val request = pending.args?.actionRequest
                if (request != null) {
                    HumanResponse(
                        type = HumanResponseType.ACCEPT,
                        args = HumanResponseArgs.Action(stringifiedArgs(request)),
                    )
                } else {
                    HumanResponse(type = HumanResponseType.ACCEPT, args = HumanResponseArgs.NullArg)
                }
            }
            HumanResponseType.EDIT -> {
                var request = pending.args?.actionRequest ?: ActionRequest("", emptyMap())
                val merged = editedArgs.mapValues { (_, value) -> JsonValue.Str(value) }
                request = request.copy(args = merged)
                if (pending.acceptAllowed && !pending.editsMade) {
                    HumanResponse(
                        type = HumanResponseType.ACCEPT,
                        args = HumanResponseArgs.Action(request),
                    )
                } else {
                    HumanResponse(
                        type = HumanResponseType.EDIT,
                        args = HumanResponseArgs.Action(request),
                    )
                }
            }
        }
    }

    private fun stringifiedArgs(request: ActionRequest): ActionRequest {
        val args = request.args.mapValues { (_, value) -> stringify(value) }
        return request.copy(args = args)
    }

    private fun stringify(value: JsonValue): JsonValue {
        return when (value) {
            is JsonValue.Str, JsonValue.Null -> value
            is JsonValue.Bool -> JsonValue.Str(if (value.value) "true" else "false")
            is JsonValue.Num -> {
                val number = value.value
                if (number % 1.0 == 0.0) JsonValue.Str(number.toLong().toString())
                else JsonValue.Str(number.toString())
            }
            is JsonValue.Obj, is JsonValue.Arr -> {
                runCatching {
                    JsonValue.Str(AppJson.encodeToString(value.toJsonElement()))
                }.getOrDefault(JsonValue.Str(""))
            }
        }
    }
}

data class PendingHumanResponse(
    val id: String = UUID.randomUUID().toString(),
    val type: HumanResponseType,
    var args: PendingArgs?,
    var acceptAllowed: Boolean,
    var editsMade: Boolean,
)

sealed class PendingArgs {
    data object NullArg : PendingArgs()
    data class Text(val value: String) : PendingArgs()
    data class Action(val request: ActionRequest) : PendingArgs()

    val actionRequest: ActionRequest?
        get() = (this as? Action)?.request
}
