package com.smartstorage.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartstorage.app.data.InventoryRepository
import com.smartstorage.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RepositoryTest {
    @Test fun restorePreservesPngExtensionAndBytes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("smart-storage.db")
        val repo = InventoryRepository(context)
        try {
            repo.load()
            val png = TestPng.bytes
            val state = InventoryRules.add(Inventory(), Thing("cutout", "透明物品", photo = "cutout.png"),
                Batch("batch", "cutout"), 1, UNPLACED, "import")
            repo.restore(RestoredArchive(state, mapOf("cutout.png" to png)))
            repo.load()
            val name = repo.state.value!!.items.single().photo!!
            assertTrue(name.endsWith(".png"))
            assertArrayEquals(png, java.io.File(repo.photos, name).readBytes())
        } finally { repo.close(); context.deleteDatabase("smart-storage.db") }
    }
    @Test fun containerLayersSurviveDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("smart-storage.db")
        var repo = InventoryRepository(context)
        try {
            repo.load()
            repo.update { s -> InventoryRules.setContainerLayers(s.copy(places = s.places + listOf(
                Place("home", kind = "HOUSE", name = "家"), Place("room", "home", "ROOM", "客厅"),
                Place("box", "room", "CONTAINER", "柜子"))), "box", 3) }
            val expected = repo.state.value!!.layers("box")
            repo.close(); repo = InventoryRepository(context); repo.load()
            assertEquals(expected, repo.state.value!!.layers("box"))
            assertEquals(2, repo.state.value!!.version)
        } finally { repo.close(); context.deleteDatabase("smart-storage.db") }
    }
    @Test fun invalidRestoreLeavesExistingStateUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); context.deleteDatabase("smart-storage.db")
        val repo = InventoryRepository(context)
        try {
            repo.load(); val original = repo.state.value!!
            var failed = false
            try { repo.restore(RestoredArchive(original.copy(balances = listOf(Balance(batchId = "missing", quantity = 1))), emptyMap())) }
            catch (_: IllegalArgumentException) { failed = true }
            assertTrue(failed); assertEquals(original, repo.state.value)
            repo.load(); assertEquals(original, repo.state.value)
        } finally { repo.close(); context.deleteDatabase("smart-storage.db") }
    }
    @Test fun survivesReopenWithBalancesAndHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("smart-storage.db")
        var repo = InventoryRepository(context)
        try {
            repo.load()
            repo.update { InventoryRules.add(it, Thing("i", "洗发水", unit = "瓶"), Batch("b", "i", price = 1500), 3, UNPLACED, "add") }
            repo.update { InventoryRules.change(it, it.balances.single().id, 1, "消耗", operationId = "consume") }
            val expected = repo.state.value
            repo.close(); repo = InventoryRepository(context); repo.load()
            assertEquals(expected, repo.state.value)
            assertEquals(2, repo.state.value!!.balances.single().quantity)
            assertEquals(2, repo.state.value!!.movements.size)
        } finally { repo.close(); context.deleteDatabase("smart-storage.db") }
    }
    @Test fun restoreReplacesLibraryAndPreservesSafetySnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); context.deleteDatabase("smart-storage.db")
        val repo = InventoryRepository(context)
        try {
            repo.load()
            repo.update { InventoryRules.add(it, Thing("old", "旧物品"), Batch("old-b", "old"), 1, UNPLACED, "old-op") }
            val previous = repo.state.value!!
            val next = InventoryRules.add(Inventory(), Thing("new", "新物品"), Batch("new-b", "new"), 2, UNPLACED, "new-op")
            repo.restore(RestoredArchive(next, emptyMap()))
            assertEquals(next, repo.state.value)
            val safe = repo.safetyBackup.inputStream().use { Archive.read(it) }
            assertEquals(previous, safe.state)
            repo.load(); assertEquals(next, repo.state.value)
        } finally { repo.close(); context.deleteDatabase("smart-storage.db") }
    }
}
