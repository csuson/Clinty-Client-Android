package com.clinty.client.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clinty.client.models.ThreadData
import com.clinty.client.services.BackgroundRefreshScheduler
import com.clinty.client.services.InboxNotificationService
import com.clinty.client.services.InboxRefreshTracker
import com.clinty.client.services.InboxStore
import com.clinty.client.services.LangGraphClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InboxListViewModel(
    private val store: InboxStore,
    application: Application,
    private val client: LangGraphClient = LangGraphClient(store),
) : AndroidViewModel(application) {

    private val _threads = MutableStateFlow<List<ThreadData>>(emptyList())
    val threads: StateFlow<List<ThreadData>> = _threads.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var refreshIntervalSeconds: Long = DEFAULT_REFRESH_INTERVAL_SECONDS
    private var periodicRefreshJob: Job? = null
    private var isPeriodicRefreshRunning = false
    private val refreshMutex = Mutex()

    val selectedInboxName: String
        get() = store.selectedInbox?.displayName ?: "No inbox"

    fun setRefreshIntervalSeconds(seconds: Long) {
        if (seconds <= 0) return
        refreshIntervalSeconds = seconds
        if (isPeriodicRefreshRunning) {
            startPeriodicRefresh()
        }
    }

    fun handleAppForegrounded() {
        if (!isPeriodicRefreshRunning) {
            startPeriodicRefresh()
        }
    }

    fun handleAppBackgrounded() {
        BackgroundRefreshScheduler.schedule(
            getApplication(),
            refreshIntervalSeconds,
        )
    }

    fun startPeriodicRefresh() {
        stopPeriodicRefresh()
        isPeriodicRefreshRunning = true
        periodicRefreshJob = viewModelScope.launch {
            refresh(showLoading = _threads.value.isEmpty())

            while (isActive) {
                delay(refreshIntervalSeconds * 1_000L)
                refresh(showLoading = false)
            }

            isPeriodicRefreshRunning = false
        }
    }

    fun stopPeriodicRefresh() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
        isPeriodicRefreshRunning = false
    }

    fun refresh(showLoading: Boolean = true) {
        if (!store.isConfigured) {
            _threads.value = emptyList()
            InboxRefreshTracker.reset()
            return
        }

        viewModelScope.launch {
            refreshInternal(showLoading)
        }
    }

    private suspend fun refreshInternal(showLoading: Boolean) {
        refreshMutex.withLock {
            if (showLoading) _isLoading.value = true
            _errorMessage.value = null
            try {
                val fetchedThreads = client.searchThreads()
                notifyIfNewThreads(fetchedThreads)
                _threads.value = fetchedThreads
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }
    }

    private fun notifyIfNewThreads(threads: List<ThreadData>) {
        val newThreads = InboxRefreshTracker.detectNewThreads(threads)
        if (newThreads.isNotEmpty()) {
            InboxNotificationService.notifyNewInterrupts(getApplication(), newThreads)
        }
    }

    override fun onCleared() {
        stopPeriodicRefresh()
        super.onCleared()
    }

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 120L
    }
}
