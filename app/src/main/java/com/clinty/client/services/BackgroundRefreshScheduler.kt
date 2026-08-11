package com.clinty.client.services

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.clinty.client.viewmodels.InboxListViewModel
import java.util.concurrent.TimeUnit

object BackgroundRefreshScheduler {
    private const val WORK_NAME = "inbox_refresh"

    fun schedule(
        context: Context,
        refreshIntervalSeconds: Long = InboxListViewModel.DEFAULT_REFRESH_INTERVAL_SECONDS,
    ) {
        val request = OneTimeWorkRequestBuilder<InboxRefreshWorker>()
            .setInitialDelay(refreshIntervalSeconds, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
