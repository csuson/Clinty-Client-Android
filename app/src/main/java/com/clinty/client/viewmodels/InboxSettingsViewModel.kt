package com.clinty.client.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinty.client.LocalSecretsDefaults
import com.clinty.client.models.AgentInbox
import com.clinty.client.services.InboxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InboxSettingsViewModel(
    private val store: InboxStore,
) : ViewModel() {

    private val _clintAPIKey = MutableStateFlow(store.clintAPIKey.value)
    val clintAPIKey: StateFlow<String> = _clintAPIKey.asStateFlow()

    private val _langsmithAPIKey = MutableStateFlow(store.langsmithAPIKey.value)
    val langsmithAPIKey: StateFlow<String> = _langsmithAPIKey.asStateFlow()

    private val _inboxes = MutableStateFlow(store.inboxes.value)
    val inboxes: StateFlow<List<AgentInbox>> = _inboxes.asStateFlow()

    private val _newGraphId = MutableStateFlow(LocalSecretsDefaults.graphId.orEmpty())
    val newGraphId: StateFlow<String> = _newGraphId.asStateFlow()

    private val _newDeploymentURL = MutableStateFlow(LocalSecretsDefaults.deploymentUrl.orEmpty())
    val newDeploymentURL: StateFlow<String> = _newDeploymentURL.asStateFlow()

    private val _newInboxName = MutableStateFlow("")
    val newInboxName: StateFlow<String> = _newInboxName.asStateFlow()

    private val _editingInboxId = MutableStateFlow<String?>(null)
    val editingInboxId: StateFlow<String?> = _editingInboxId.asStateFlow()

    private val _editGraphId = MutableStateFlow("")
    val editGraphId: StateFlow<String> = _editGraphId.asStateFlow()

    private val _editDeploymentURL = MutableStateFlow("")
    val editDeploymentURL: StateFlow<String> = _editDeploymentURL.asStateFlow()

    private val _editInboxName = MutableStateFlow("")
    val editInboxName: StateFlow<String> = _editInboxName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateClintAPIKey(value: String) {
        _clintAPIKey.value = value
    }

    fun updateLangsmithAPIKey(value: String) {
        _langsmithAPIKey.value = value
    }

    fun updateNewGraphId(value: String) {
        _newGraphId.value = value
    }

    fun updateNewDeploymentURL(value: String) {
        _newDeploymentURL.value = value
    }

    fun updateNewInboxName(value: String) {
        _newInboxName.value = value
    }

    fun updateEditGraphId(value: String) {
        _editGraphId.value = value
    }

    fun updateEditDeploymentURL(value: String) {
        _editDeploymentURL.value = value
    }

    fun updateEditInboxName(value: String) {
        _editInboxName.value = value
    }

    fun saveAPIKeys() {
        store.setClintAPIKey(_clintAPIKey.value)
        store.setLangsmithAPIKey(_langsmithAPIKey.value)
    }

    fun startEditingInbox(inbox: AgentInbox) {
        _editingInboxId.value = inbox.id
        _editGraphId.value = inbox.graphId
        _editDeploymentURL.value = inbox.deploymentUrl
        _editInboxName.value = inbox.name.orEmpty()
        _errorMessage.value = null
    }

    fun cancelEditingInbox() {
        _editingInboxId.value = null
        _editGraphId.value = ""
        _editDeploymentURL.value = ""
        _editInboxName.value = ""
        _errorMessage.value = null
    }

    fun addInbox(onSuccess: () -> Unit) {
        val graphId = _newGraphId.value.trim()
        val url = _newDeploymentURL.value.trim()
        if (graphId.isEmpty() || url.isEmpty()) {
            _errorMessage.value = "Graph ID and deployment URL are required."
            return
        }

        viewModelScope.launch {
            try {
                store.addInbox(graphId, url, _newInboxName.value.trim().ifEmpty { null })
                _inboxes.value = store.inboxes.value
                _newGraphId.value = ""
                _newDeploymentURL.value = ""
                _newInboxName.value = ""
                _errorMessage.value = null
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun saveEditedInbox() {
        val id = _editingInboxId.value ?: return
        val graphId = _editGraphId.value.trim()
        val url = _editDeploymentURL.value.trim()
        if (graphId.isEmpty() || url.isEmpty()) {
            _errorMessage.value = "Graph ID and deployment URL are required."
            return
        }

        viewModelScope.launch {
            try {
                store.updateInbox(id, graphId, url, _editInboxName.value.trim().ifEmpty { null })
                _inboxes.value = store.inboxes.value
                cancelEditingInbox()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun selectInbox(inbox: AgentInbox) {
        store.selectInbox(inbox.id)
        _inboxes.value = store.inboxes.value
    }

    fun deleteInbox(inbox: AgentInbox) {
        if (_editingInboxId.value == inbox.id) {
            cancelEditingInbox()
        }
        store.deleteInbox(inbox.id)
        _inboxes.value = store.inboxes.value
    }
}
