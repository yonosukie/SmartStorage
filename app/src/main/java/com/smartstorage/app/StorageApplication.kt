package com.smartstorage.app

import android.app.Application
import androidx.work.*
import com.smartstorage.app.data.InventoryRepository
import java.util.concurrent.TimeUnit

class StorageApplication : Application() {
    val repository by lazy { InventoryRepository(this) }
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("expiry-daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExpiryWorker>(24, TimeUnit.HOURS).build())
    }
}
