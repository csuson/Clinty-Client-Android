package com.clinty.client.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HumanInterruptConfig(
    @SerialName("allow_ignore") val allowIgnore: Boolean,
    @SerialName("allow_respond") val allowRespond: Boolean,
    @SerialName("allow_edit") val allowEdit: Boolean,
    @SerialName("allow_accept") val allowAccept: Boolean,
)

@Serializable
data class ActionRequest(
    var action: String,
    @Serializable(with = JsonValueMapSerializer::class)
    var args: Map<String, JsonValue> = emptyMap(),
)

@Serializable
data class HumanInterrupt(
    @SerialName("action_request") val actionRequest: ActionRequest,
    val config: HumanInterruptConfig,
    val description: String? = null,
) {
    val id: String get() = actionRequest.action
}

@Serializable
enum class HumanResponseType {
    @SerialName("accept") ACCEPT,
    @SerialName("ignore") IGNORE,
    @SerialName("response") RESPONSE,
    @SerialName("edit") EDIT,
}

@Serializable
data class HumanResponse(
    val type: HumanResponseType,
    @Serializable(with = HumanResponseArgsSerializer::class)
    val args: HumanResponseArgs? = null,
)

enum class SubmitType {
    ACCEPT,
    RESPONSE,
    EDIT,
}

@Serializable
data class AgentInbox(
    val id: String,
    var graphId: String,
    var deploymentUrl: String,
    var name: String? = null,
    var selected: Boolean = false,
    var tenantId: String? = null,
    val createdAt: String,
) {
    val displayName: String
        get() = if (!name.isNullOrEmpty()) name!! else graphId
}

enum class ThreadStatusFilter(val apiValue: String) {
    INTERRUPTED("interrupted"),
}

object AgentInboxConstants {
    const val IMPROPER_SCHEMA = "improper_schema"
}
