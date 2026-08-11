package com.clinty.client.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinty.client.models.HumanInterrupt
import com.clinty.client.models.HumanResponse
import com.clinty.client.models.HumanResponseArgs
import com.clinty.client.models.HumanResponseType
import com.clinty.client.models.SubmitType
import com.clinty.client.models.ThreadData
import com.clinty.client.models.jsonValueString
import com.clinty.client.services.HumanResponseBuilder
import com.clinty.client.services.InboxStore
import com.clinty.client.services.LangGraphClient
import com.clinty.client.services.PendingHumanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThreadDetailViewModel(
    private val threadId: String,
    initial: ThreadData?,
    private val store: InboxStore,
    private val client: LangGraphClient = LangGraphClient(store),
) : ViewModel() {

    private val _threadData = MutableStateFlow(initial)
    val threadData: StateFlow<ThreadData?> = _threadData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedSubmitType = MutableStateFlow(SubmitType.RESPONSE)
    val selectedSubmitType: StateFlow<SubmitType> = _selectedSubmitType.asStateFlow()

    private val _responseText = MutableStateFlow("")
    val responseText: StateFlow<String> = _responseText.asStateFlow()

    private val _editedArgs = MutableStateFlow<Map<String, String>>(emptyMap())
    val editedArgs: StateFlow<Map<String, String>> = _editedArgs.asStateFlow()

    private val _pendingResponses = MutableStateFlow<List<PendingHumanResponse>>(emptyList())
    val pendingResponses: StateFlow<List<PendingHumanResponse>> = _pendingResponses.asStateFlow()

    private val _threadValuesText = MutableStateFlow<String?>(null)
    val threadValuesText: StateFlow<String?> = _threadValuesText.asStateFlow()

    private val _shouldReturnToInbox = MutableStateFlow(false)
    val shouldReturnToInbox: StateFlow<Boolean> = _shouldReturnToInbox.asStateFlow()

    val interrupt: HumanInterrupt?
        get() = _threadData.value?.interrupts?.firstOrNull()

    val isIdle: Boolean
        get() = _threadData.value?.status == "idle"

    val needsLoad: Boolean
        get() = _threadData.value == null || isIdle

    val canSubmit: Boolean
        get() = !_isSubmitting.value && interrupt != null && _pendingResponses.value.isNotEmpty()

    init {
        configurePendingResponses()
    }

    fun updateSelectedSubmitType(type: SubmitType) {
        _selectedSubmitType.value = type
    }

    fun updateResponseText(value: String) {
        _responseText.value = value
    }

    fun updateEditedArg(key: String, value: String) {
        _editedArgs.value = _editedArgs.value.toMutableMap().also { it[key] = value }
    }

    fun load() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                _threadData.value = client.fetchThread(threadId)
                configurePendingResponses()
                loadThreadValuesIfNeeded()
                updateShouldReturnToInbox()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
            _isLoading.value = false
        }
    }

    fun submit() {
        val currentInterrupt = interrupt ?: return
        val pending = _pendingResponses.value.firstOrNull { matchesSubmitType(it.type) } ?: return
        _isSubmitting.value = true
        _errorMessage.value = null

        val submission = pending.copy(editsMade = haveArgsChanged)
        val submitType = _selectedSubmitType.value

        viewModelScope.launch {
            try {
                val response = HumanResponseBuilder.buildSubmission(
                    pending = submission,
                    responseText = _responseText.value,
                    editedArgs = _editedArgs.value,
                )
                client.sendHumanResponse(threadId, listOf(response))
                val updated = client.fetchThread(threadId)
                _threadData.value = updated
                configurePendingResponses()
                if (updated.status != "interrupted") {
                    _responseText.value = ""
                }
                if (submitType == SubmitType.ACCEPT) {
                    _shouldReturnToInbox.value = true
                } else {
                    updateShouldReturnToInbox()
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
            _isSubmitting.value = false
        }
    }

    fun ignoreInterrupt() {
        _isSubmitting.value = true
        viewModelScope.launch {
            try {
                client.sendHumanResponse(
                    threadId,
                    listOf(HumanResponse(HumanResponseType.IGNORE, HumanResponseArgs.NullArg)),
                )
                _threadData.value = client.fetchThread(threadId)
                configurePendingResponses()
                updateShouldReturnToInbox()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
            _isSubmitting.value = false
        }
    }

    fun resolveThread(onComplete: (Boolean) -> Unit) {
        _isSubmitting.value = true
        viewModelScope.launch {
            val success = try {
                client.resolveThread(threadId)
                true
            } catch (e: Exception) {
                _errorMessage.value = e.message
                false
            }
            _isSubmitting.value = false
            onComplete(success)
        }
    }

    private val haveArgsChanged: Boolean
        get() {
            val original = interrupt?.actionRequest?.args ?: return false
            return _editedArgs.value.any { (key, value) ->
                jsonValueString(original[key]) != value
            }
        }

    private suspend fun loadThreadValuesIfNeeded() {
        if (!isIdle) {
            _threadValuesText.value = null
            return
        }
        try {
            val state = client.fetchThreadState(threadId)
            _threadValuesText.value = state.values?.prettyPrinted()
        } catch (e: Exception) {
            _errorMessage.value = e.message
            _threadValuesText.value = null
        }
    }

    private fun configurePendingResponses() {
        val currentInterrupt = interrupt
        if (currentInterrupt == null) {
            _pendingResponses.value = emptyList()
            return
        }
        val built = HumanResponseBuilder.defaultResponses(currentInterrupt)
        _pendingResponses.value = built.responses
        built.defaultSubmit?.let { _selectedSubmitType.value = it }
        _editedArgs.value = currentInterrupt.actionRequest.args.mapValues { (_, value) ->
            jsonValueString(value)
        }
    }

    private fun updateShouldReturnToInbox() {
        if (_isLoading.value) {
            _shouldReturnToInbox.value = false
            return
        }
        val data = _threadData.value
        _shouldReturnToInbox.value = data != null &&
            interrupt == null &&
            !isIdle &&
            !data.invalidSchema
    }

    private fun matchesSubmitType(type: HumanResponseType): Boolean {
        return when (_selectedSubmitType.value to type) {
            SubmitType.ACCEPT to HumanResponseType.ACCEPT,
            SubmitType.ACCEPT to HumanResponseType.EDIT,
            SubmitType.RESPONSE to HumanResponseType.RESPONSE,
            SubmitType.EDIT to HumanResponseType.EDIT -> true
            else -> false
        }
    }
}
