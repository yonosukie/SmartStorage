package com.smartstorage.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val repository = (applicationContext as StorageApplication).repository
        if (repository.state.value == null) repository.load()
        val state = requireNotNull(repository.state.value); val today = LocalDate.now().toString()
        val permitted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (state.preferences.reminderEnabled && permitted && state.preferences.lastNotified != today) {
            val rows = state.rows().filter { it.batch.reminderEnabled && it.balance.quantity > 0 && (it.expired() || it.due(state.preferences.leadDays)) }
            if (rows.isNotEmpty()) {
                val manager = applicationContext.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("expiry", "保质期提醒", NotificationManager.IMPORTANCE_DEFAULT))
                val pending = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                manager.notify(1001, NotificationCompat.Builder(applicationContext, "expiry").setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("留意家里的保质期")
                    .setContentText("${rows.map { it.batch.id }.distinct().size} 个批次临期或已过期，点击查看")
                    .setContentIntent(pending).setAutoCancel(true).build())
                repository.update { it.copy(preferences = it.preferences.copy(lastNotified = today)) }
            }
        }; Result.success()
    } catch (_: Exception) { Result.retry() }
}
