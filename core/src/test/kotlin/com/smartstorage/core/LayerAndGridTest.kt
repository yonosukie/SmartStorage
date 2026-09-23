package com.smartstorage.core

import org.junit.Assert.*
import org.junit.Test

class LayerAndGridTest {
    private fun layered(): Inventory {
        val s = Inventory(places = Inventory().places + listOf(
            Place("home", kind = "HOUSE", name = "家"),
            Place("room", "home", "ROOM", "卧室"), Place("box", "room", "CONTAINER", "衣柜")))
        return InventoryRules.setContainerLayers(s, "box", 3)
    }
    @Test fun movingAcrossLayersPreservesQuantityValueAndHierarchy() {
        val s = layered(); val layers = s.layers("box")
        val item = Thing("shirt", "衬衫", "衣物", "件")
        val stocked = InventoryRules.add(s, item, Batch("batch", item.id, price = 2500), 5, layers[0].id, "add")
        val moved = InventoryRules.change(stocked, stocked.balances.first().id, 2, "移动", layers[1].id)
        assertEquals(5, moved.balances.sumOf { it.quantity })
        assertEquals(12500L, moved.rows().sumOf { it.value })
        assertEquals(2, moved.filtered(Filter(place = layers[1].id)).sumOf { it.balance.quantity })
        assertEquals(5, moved.filtered(Filter(place = "room")).sumOf { it.balance.quantity })
        assertTrue(moved.path(layers[1].id).contains("第 2 层"))
    }
    @Test fun cannotShrinkAnOccupiedLayer() {
        val s = layered(); val item = Thing("a", "物品")
        val stocked = InventoryRules.add(s, item, Batch(itemId = item.id), 1, s.layers("box").last().id, "add")
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.setContainerLayers(stocked, "box", 2) }
    }
    @Test fun shrinkingPreservesHistoricalIdsAndExpansionReusesThem() {
        val s = layered(); val old = s.layers("box")
        val shrunk = InventoryRules.setContainerLayers(s, "box", 1)
        assertEquals(1, shrunk.layers("box").size)
        assertTrue(shrunk.places.first { it.id == old.last().id }.archived)
        assertEquals(old, InventoryRules.setContainerLayers(shrunk, "box", 3).layers("box"))
    }
    @Test fun shrinkingRejectsNestedContainers() {
        val s = layered()
        val nested = InventoryRules.savePlace(s, Place("nested", s.layers("box").last().id, "CONTAINER", "收纳盒"))
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.setContainerLayers(nested, "box", 0) }
    }
    @Test fun cannotUndoStockIntoRemovedLayer() {
        val s = layered(); val item = Thing("a", "物品")
        val stocked = InventoryRules.add(s, item, Batch(itemId = item.id), 1, s.layers("box").last().id, "add")
        val consumed = InventoryRules.change(stocked, stocked.balances.first().id, 1, "消耗", operationId = "consume")
        val shrunk = InventoryRules.setContainerLayers(consumed, "box", 0)
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.undo(shrunk, "consume") }
    }
    @Test fun oldRectanglesAndEdgesSnapToSameTenCellGrid() {
        val box = LayoutGrid.snap(LayoutBox("a", "room", x = .97f, y = -.2f, width = .28f, height = .24f))
        assertEquals(.7f, box.x, .00001f); assertEquals(0f, box.y, .00001f)
        assertEquals(.3f, box.width, .00001f); assertEquals(.2f, box.height, .00001f)
        assertEquals(box, LayoutGrid.snap(box))
    }
    @Test fun resizingAndRotationStayInsideRoom() {
        val box = LayoutGrid.snap(LayoutBox("a", "room", x = .8f, y = .8f, width = .1f, height = .2f))
        val rotated = LayoutGrid.snap(box.copy(width = box.height, height = box.width))
        assertTrue(rotated.x + rotated.width <= 1.001f)
        val large = LayoutGrid.snap(box.copy(width = 2f, height = 2f))
        assertEquals(0f, large.x, 0f); assertEquals(1f, large.width, 0f)
        assertThrows(IllegalArgumentException::class.java) { LayoutGrid.snap(box.copy(x = Float.NaN)) }
    }
    @Test fun rectangularResizeChangesAxesIndependently() {
        val original = LayoutBox("box", "room", x = .7f, y = .6f, width = .2f, height = .2f)
        val wide = LayoutGrid.resize(original, 6, 2)
        assertEquals(.6f, wide.width, .00001f); assertEquals(original.height, wide.height, .00001f)
        assertEquals(.4f, wide.x, .00001f); assertEquals(original.y, wide.y, .00001f)
        val tall = LayoutGrid.resize(wide, 6, 8)
        assertEquals(wide.width, tall.width, .00001f); assertEquals(.8f, tall.height, .00001f)
        assertEquals(.2f, tall.y, .00001f)
    }
    @Test fun oldAndLayeredBackupsRoundTrip() {
        listOf(Inventory(version = 1), layered()).forEach { original ->
            val bytes = java.io.ByteArrayOutputStream()
            Archive.write(original, { byteArrayOf() }, bytes)
            assertEquals(original, Archive.read(bytes.toByteArray().inputStream()).state)
        }
    }
}
