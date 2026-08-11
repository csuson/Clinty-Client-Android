package com.clinty.client.services

import android.content.Context
import com.clinty.client.LocalSecretsDefaults
import com.clinty.client.models.AgentInbox
import com.clinty.client.models.currentIsoTimestamp
import com.clinty.client.services.LangGraphClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.UUID

class InboxStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _clintAPIKey = MutableStateFlow(loadClintApiKey())
    val clintAPIKey: StateFlow<String> = _clintAPIKey.asStateFlow()

    private val _langsmithAPIKey = MutableStateFlow(loadLangsmithApiKey())
    val langsmithAPIKey: StateFlow<String> = _langsmithAPIKey.asStateFlow()

    private val _inboxes = MutableStateFlow(loadInboxes())
    val inboxes: StateFlow<List<AgentInbox>> = _inboxes.asStateFlow()

    val selectedInbox: AgentInbox?
        get() = _inboxes.value.firstOrNull { it.selected }

    val isConfigured: Boolean
        get() = selectedInbox != null

    fun setClintAPIKey(value: String) {
        val trimmed = value.trim()
        _clintAPIKey.value = trimmed
        prefs.edit().putString(KEY_CLINT_API, trimmed).apply()
    }

    fun setLangsmithAPIKey(value: String) {
        val trimmed = value.trim()
        _langsmithAPIKey.value = trimmed
        prefs.edit().putString(KEY_API, trimmed).apply()
    }

    suspend fun addInbox(graphId: String, deploymentUrl: String, name: String?): AgentInbox {
        val trimmedUrl = DeploymentURLNormalizer.normalize(deploymentUrl)
        var inboxId = UUID.randomUUID().toString()
        var tenantId: String? = null

        if (isDeployedUrl(trimmedUrl)) {
            val info = LangGraphClient(this).fetchDeploymentInfo(trimmedUrl)
            info?.host?.projectId?.let { projectId ->
                inboxId = "$projectId:$graphId"
            }
            tenantId = info?.host?.tenantId
        }

        val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val inbox = AgentInbox(
            id = inboxId,
            graphId = graphId.trim(),
            deploymentUrl = trimmedUrl,
            name = trimmedName,
            selected = true,
            tenantId = tenantId,
            createdAt = currentIsoTimestamp(),
        )

        _inboxes.update { existing ->
            existing.map { it.copy(selected = false) } + inbox
        }
        persistInboxes()
        return inbox
    }

    suspend fun updateInbox(
        id: String,
        graphId: String,
        deploymentUrl: String,
        name: String?,
    ): AgentInbox {
        val existing = _inboxes.value.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Inbox not found.")

        val trimmedUrl = DeploymentURLNormalizer.normalize(deploymentUrl)
        val trimmedGraphId = graphId.trim()
        val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }

        var tenantId = existing.tenantId
        if (isDeployedUrl(trimmedUrl)) {
            val info = LangGraphClient(this).fetchDeploymentInfo(trimmedUrl)
            tenantId = info?.host?.tenantId
        } else {
            tenantId = null
        }

        val updated = existing.copy(
            graphId = trimmedGraphId,
            deploymentUrl = trimmedUrl,
            name = trimmedName,
            tenantId = tenantId,
        )

        _inboxes.update { list ->
            list.map { if (it.id == id) updated else it }
        }
        persistInboxes()
        return updated
    }

    fun selectInbox(id: String) {
        _inboxes.update { list ->
            list.map { it.copy(selected = it.id == id) }
        }
        persistInboxes()
    }

    fun deleteInbox(id: String) {
        var updated = _inboxes.value.filter { it.id != id }
        if (updated.none { it.selected } && updated.isNotEmpty()) {
            updated = updated.toMutableList().also { it[0] = it[0].copy(selected = true) }
        }
        _inboxes.value = updated
        persistInboxes()
    }

    fun normalizedBaseUrl(inbox: AgentInbox): java.net.URL {
        val raw = DeploymentURLNormalizer.normalize(inbox.deploymentUrl)
        val url = runCatching { URL(raw) }.getOrNull()
        if (url == null || url.host.isNullOrEmpty() || url.protocol.isNullOrEmpty()) {
            throw LangGraphClient.LangGraphClientError.InvalidURL
        }
        return url
    }

    fun requiresAPIKey(inbox: AgentInbox): Boolean {
        return inbox.deploymentUrl.contains("us.langgraph.app") ||
            inbox.deploymentUrl.contains("langgraph.app")
    }

    private fun loadClintApiKey(): String {
        val stored = prefs.getString(KEY_CLINT_API, null)?.trim().orEmpty()
        if (stored.isNotEmpty()) return stored
        val local = LocalSecretsDefaults.clintAPIKey?.trim().orEmpty()
        if (local.isNotEmpty()) {
            prefs.edit().putString(KEY_CLINT_API, local).apply()
            return local
        }
        return ""
    }

    private fun loadLangsmithApiKey(): String {
        val stored = prefs.getString(KEY_API, null)?.trim().orEmpty()
        if (stored.isNotEmpty()) return stored
        val local = LocalSecretsDefaults.langsmithAPIKey?.trim().orEmpty()
        if (local.isNotEmpty()) {
            prefs.edit().putString(KEY_API, local).apply()
            return local
        }
        return ""
    }

    private fun loadInboxes(): List<AgentInbox> {
        val stored = prefs.getString(KEY_INBOXES, null)
        if (!stored.isNullOrEmpty()) {
            val decoded = runCatching {
                json.decodeFromString<List<AgentInbox>>(stored)
            }.getOrNull()
            if (!decoded.isNullOrEmpty()) {
                return repairDeploymentUrls(decoded)
            }
        }
        return makeDefaultInboxes()
    }

    private fun repairDeploymentUrls(inboxes: List<AgentInbox>): List<AgentInbox> {
        return inboxes.map { inbox ->
            inbox.copy(deploymentUrl = DeploymentURLNormalizer.normalize(inbox.deploymentUrl))
        }
    }

    private fun makeDefaultInboxes(): List<AgentInbox> {
        val graphId = LocalSecretsDefaults.graphId?.trim().orEmpty()
        val deploymentUrl = DeploymentURLNormalizer.normalize(
            LocalSecretsDefaults.deploymentUrl?.trim().orEmpty(),
        )
        if (graphId.isEmpty() || deploymentUrl.isEmpty()) return emptyList()
        return listOf(
            AgentInbox(
                id = UUID.randomUUID().toString(),
                graphId = graphId,
                deploymentUrl = deploymentUrl,
                name = "Gmail Assistant",
                selected = true,
                tenantId = null,
                createdAt = currentIsoTimestamp(),
            ),
        )
    }

    private fun persistInboxes() {
        val encoded = json.encodeToString(_inboxes.value)
        prefs.edit().putString(KEY_INBOXES, encoded).apply()
        if (_inboxes.value.isNotEmpty() && prefs.getString(KEY_INBOXES, null) == null) {
            // ensure first save on init
        }
    }

    init {
        if (_inboxes.value.isNotEmpty()) {
            persistInboxes()
        }
    }

    private fun isDeployedUrl(url: String): Boolean {
        val parsed = runCatching { URL(url) }.getOrNull() ?: return false
        return parsed.protocol == "https" && !(parsed.host?.contains("localhost") ?: false)
    }

    companion object {
        private const val PREFS_NAME = "clinty_inbox_store"
        private const val KEY_CLINT_API = "inbox:clint_api_key"
        private const val KEY_API = "inbox:langchain_api_key"
        private const val KEY_INBOXES = "inbox:agent_inboxes"

        @Volatile
        private var instance: InboxStore? = null

        fun getInstance(context: Context): InboxStore {
            return instance ?: synchronized(this) {
                instance ?: InboxStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
