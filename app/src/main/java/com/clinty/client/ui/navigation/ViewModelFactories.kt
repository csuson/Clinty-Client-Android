package com.clinty.client.ui.navigation

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clinty.client.models.ThreadData
import com.clinty.client.services.InboxStore
import com.clinty.client.viewmodels.InboxListViewModel
import com.clinty.client.viewmodels.InboxSettingsViewModel
import com.clinty.client.viewmodels.ThreadDetailViewModel

class InboxViewModelFactory(
    private val store: InboxStore,
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InboxListViewModel::class.java)) {
            return InboxListViewModel(store, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsViewModelFactory(
    private val store: InboxStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InboxSettingsViewModel::class.java)) {
            return InboxSettingsViewModel(store) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ThreadDetailViewModelFactory(
    private val threadId: String,
    private val initial: ThreadData?,
    private val store: InboxStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThreadDetailViewModel::class.java)) {
            return ThreadDetailViewModel(threadId, initial, store) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
