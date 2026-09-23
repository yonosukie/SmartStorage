package com.smartstorage.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InventoryRulesTest {
    private val home = Place("home", kind = "HOUSE", name = "家")
    private val room = Place("room", "home", "ROOM", "厨房")
    private val box = Place("box", "room", "CONTAINER", "冰箱")
    private val thing = Thing("milk", "牛奶", "食品", "盒")
    private fun stock(expiry: String? = null): Inventory {
        val s = Inventory(places = Inventory().places + listOf(home, room, box))
        return InventoryRules.add(s, thing, Batch("batch", thing.id, expires = expiry, price = 350), 6, room.id, "initial")
    }
    @Test fun healthyExpiryFilterExcludesDueAndUnknownWithBatchOverrides() {
        val today = LocalDate.of(2026, 9, 23)
        var state = stock(today.plusDays(5).toString())
        state = InventoryRules.add(state, thing, Batch("healthy", thing.id, expires = today.plusDays(20).toString()), 1, "room", "healthy")
        state = InventoryRules.add(state, thing, Batch("override", thing.id, expires = today.plusDays(20).toString(), leadDays = 30), 1, "room", "override")
        state = InventoryRules.add(state, thing, Batch("unknown", thing.id), 1, "room", "unknown")
        assertEquals(listOf("healthy"), state.filtered(Filter(expiry = "有效期内"), today).map { it.batch.id })
    }
    @Test fun partialMovePreservesBatchAndTotal() {
        val s = stock("2030-10-01"); val moved = InventoryRules.change(s, s.balances.first().id, 2, "移动", "box")
        assertEquals(6, moved.balances.sumOf { it.quantity }); assertEquals(4, moved.balances.first { it.placeId == "room" }.quantity)
        assertEquals(2, moved.balances.first { it.placeId == "box" }.quantity); assertEquals(s.batches, moved.batches)
        assertEquals(2100L, moved.rows().sumOf { it.value })
    }
    @Test fun repeatOperationIsIdempotent() {
        val s = stock(); val first = InventoryRules.change(s, s.balances.first().id, 1, "消耗", operationId = "consume")
        assertEquals(first, InventoryRules.change(first, s.balances.first().id, 1, "消耗", operationId = "consume"))
    }
    @Test fun cannotConsumeMoreThanAvailable() {
        val s = stock(); assertThrows(IllegalArgumentException::class.java) { InventoryRules.change(s, s.balances.first().id, 7, "消耗") }
        assertEquals(6, s.balances.first().quantity)
    }
    @Test fun expiredConsumptionRequiresConfirmation() {
        val s = stock(LocalDate.now().minusDays(1).toString())
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.change(s, s.balances.first().id, 1, "消耗") }
        assertEquals(5, InventoryRules.change(s, s.balances.first().id, 1, "消耗", allowExpired = true).balances.first().quantity)
    }
    @Test fun expiryDayIsInclusive() {
        val today = LocalDate.of(2026, 9, 22); val row = stock(today.toString()).rows().first()
        assertFalse(row.expired(today)); assertTrue(row.due(0, today)); assertTrue(row.expired(today.plusDays(1)))
    }
    @Test fun expiredDiscardPreservesHistoricalPrice() {
        val s = stock("2020-01-01"); val next = InventoryRules.change(s, s.balances.first().id, 1, "丢弃")
        assertEquals("过期丢弃", next.movements.last().reason); assertEquals(350L, next.movements.last().price)
    }
    @Test fun undoMoveRestoresBothSides() {
        val s = stock(); val moved = InventoryRules.change(s, s.balances.first().id, 2, "移动", "box", "move")
        val undone = InventoryRules.undo(moved, "move")
        assertEquals(6, undone.balances.first { it.placeId == "room" }.quantity); assertEquals(0, undone.balances.first { it.placeId == "box" }.quantity)
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.undo(undone, "move") }
    }
    @Test fun undoCannotOverwriteLaterConsumption() {
        val s = stock(); val first = InventoryRules.change(s, s.balances.first().id, 1, "消耗", operationId = "one")
        val second = InventoryRules.change(first, s.balances.first().id, 1, "消耗", operationId = "two")
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.undo(second, "one") }
    }
    @Test fun locationDateAndPriceMustMatchSameBatch() {
        val s = stock("2030-01-01")
        val newer = InventoryRules.add(s, thing, Batch("second", thing.id, expires = "2031-01-01", price = 1000), 2, "box", "second-add")
        assertTrue(newer.filtered(Filter(place = "box", maxPrice = 500)).isEmpty())
        assertTrue(newer.filtered(Filter(place = "box", dateTo = "2030-06-01")).isEmpty())
        assertEquals(1, newer.filtered(Filter(place = "box", minPrice = 500)).size)
    }
    @Test fun filtersIncludeDescendantsAndChineseSearch() {
        val s = stock(); assertEquals(1, s.filtered(Filter(query = "牛", place = "home")).size)
    }
    @Test fun emptyStockExcludedUnlessRequested() {
        val s = stock(); val empty = InventoryRules.change(s, s.balances.first().id, 6, "消耗")
        assertTrue(empty.filtered(Filter()).isEmpty()); assertEquals(1, empty.filtered(Filter(includeEmpty = true)).size)
    }
    @Test fun rejectSpaceCycle() {
        val s = stock(); val child = Place("child", "box", "CONTAINER", "抽屉")
        val next = InventoryRules.savePlace(s, child)
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.savePlace(next, box.copy(parentId = "child")) }
    }
    @Test fun rejectDuplicateSiblingName() {
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.savePlace(stock(), Place(parentId = "room", kind = "CONTAINER", name = "冰箱")) }
    }
    @Test fun noSilentDeletionOfNonemptySpace() {
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.deletePlace(stock(), "room") }
    }
    @Test fun removingLabelKeepsItem() {
        val tag = Label("tag", "常用"); val s = stock().copy(labels = listOf(tag), items = listOf(thing.copy(tags = listOf("tag"))))
        val next = InventoryRules.removeLabel(s, "tag"); assertEquals(1, next.items.size); assertTrue(next.items.first().tags.isEmpty())
    }
    @Test fun moneyDoesNotUseFloatingPoint() {
        assertEquals(29L, parsePrice("0.29")); assertNull(parsePrice("")); assertEquals("0.29", money(29))
        assertThrows(ArithmeticException::class.java) { parsePrice("1.001") }
        assertThrows(IllegalArgumentException::class.java) { parsePrice("-1") }
    }
    @Test fun missingPriceNotTreatedAsFree() {
        val s = stock().copy(batches = listOf(Batch("batch", thing.id)))
        assertTrue(s.filtered(Filter(maxPrice = 0)).isEmpty()); assertEquals(1, s.filtered(Filter(unknownPrice = true)).size)
    }
}
