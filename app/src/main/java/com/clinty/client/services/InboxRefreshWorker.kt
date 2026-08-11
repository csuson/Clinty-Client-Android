package com.clinty.client.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clinty.client.viewmodels.InboxListViewModel

class InboxRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = InboxStore.getInstance(applicationContext)
        if (!store.isConfigured) {
            InboxRefreshTracker.reset()
            return Result.success()
        }

        return try {
            val threads = LangGraphClient(store).searchThreads()
            val newThreads = InboxRefreshTracker.detectNewThreads(threads)
            if (newThreads.isNotEmpty()) {
                InboxNotificationService.notifyNewInterrupts(applicationContext, newThreads)
            }
            BackgroundRefreshScheduler.schedule(
                applicationContext,
                InboxListViewModel.DEFAULT_REFRESH_INTERVAL_SECONDS,
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
