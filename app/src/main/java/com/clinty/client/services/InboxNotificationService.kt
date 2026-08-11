package com.clinty.client.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.clinty.client.R
import com.clinty.client.models.ThreadData
import com.clinty.client.models.displayFrom
import com.clinty.client.models.displayTitle

object InboxNotificationService {
    private const val CHANNEL_ID = "inbox_interrupts"
    const val EXTRA_THREAD_ID = "threadId"

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Inbox interrupts",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    fun notifyNewInterrupts(context: Context, threads: List<ThreadData>) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)
        threads.forEach { notifyNewInterrupt(context, it) }
    }

    private fun notifyNewInterrupt(context: Context, thread: ThreadData) {
        if (!hasNotificationPermission(context)) return

        val interrupt = thread.interrupts?.firstOrNull()
        val title = interrupt?.displayTitle() ?: "New message requires attention"
        val body = interrupt?.displayFrom()
            ?: "A new agent interrupt is waiting for review."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setExtras(
                android.os.Bundle().apply {
                    putString(EXTRA_THREAD_ID, thread.id)
                },
            )
            .build()

        NotificationManagerCompat.from(context).notify(
            "interrupt-${thread.id}-${System.currentTimeMillis()}".hashCode(),
            notification,
        )
    }
}
