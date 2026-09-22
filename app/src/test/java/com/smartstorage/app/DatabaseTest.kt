package com.smartstorage.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.smartstorage.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseTest {
    @Test fun foreignKeysRejectMissingBatchAndRollback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StorageDatabase::class.java).allowMainThreadQueries().build()
        try {
            var failed = false
            try { db.withTransaction {
                db.dao().places(listOf(PlaceRecord("p", "{}")))
                db.dao().balances(listOf(BalanceRecord("b", "missing", "p", 1, 0)))
            } } catch (_: Exception) { failed = true }
            assertTrue(failed); assertTrue(db.dao().places().isEmpty()); assertTrue(db.dao().balances().isEmpty())
        } finally { db.close() }
    }
    @Test fun duplicateBatchLocationRejectedWithoutLosingOriginal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StorageDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.dao().places(listOf(PlaceRecord("p", "{}"))); db.dao().items(listOf(ItemRecord("i", "{}")))
            db.dao().batches(listOf(BatchRecord("batch", "i", null, "{}")))
            db.dao().balances(listOf(BalanceRecord("b", "batch", "p", 6, 0)))
            var failed = false
            try { db.dao().balances(listOf(BalanceRecord("duplicate", "batch", "p", 2, 0))) } catch (_: Exception) { failed = true }
            assertTrue(failed); assertEquals(6, db.dao().balances().single().quantity)
        } finally { db.close() }
    }
}
