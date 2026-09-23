package com.smartstorage.app

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartstorage.app.ui.*
import com.smartstorage.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp-mdpi")
class ComponentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectorCommitsSelectedLayer() {
        var selected = ""
        compose.setContent { StorageTheme { Choice("位置", "1", listOf("1" to "第 1 层", "2" to "第 2 层")) { selected = it } } }
        compose.onNodeWithText("位置").performClick()
        compose.onNodeWithText("第 2 层").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("2", selected) }
    }
    @Test fun datePickerCancelPreservesAndClearRemovesDate() {
        var selected = "2026-09-22"
        compose.setContent { StorageTheme { DateField("购买日期", selected, { selected = it }) } }
        compose.onNodeWithText("2026-09-22").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals("2026-09-22", selected) }
        compose.onNodeWithText("2026-09-22").performClick()
        compose.onNodeWithText("清除").performClick()
        compose.runOnIdle { assertEquals("", selected) }
    }
    @Test fun cascadingPickerCommitsOnlyAfterTraversingToLayer() {
        val base = Inventory(places = Inventory().places + listOf(Place("h", kind = "HOUSE", name = "我的家"),
            Place("r", "h", "ROOM", "客厅"), Place("c", "r", "CONTAINER", "柜子")))
        val state = InventoryRules.setContainerLayers(base, "c", 2)
        var result = "unchanged"
        compose.setContent { StorageTheme { PlaceChoice(state, UNPLACED, { result = it }) } }
        compose.onNodeWithText("位置").performClick()
        compose.onNodeWithText("我的家").performClick()
        compose.onNodeWithText("选择此位置").assertIsNotEnabled()
        compose.onNodeWithText("客厅").performClick()
        compose.onNodeWithText("柜子").performClick()
        compose.onNodeWithText("第 2 层").performClick()
        compose.runOnIdle { assertEquals("unchanged", result) }
        compose.onNodeWithText("选择此位置").performClick()
        compose.runOnIdle { assertEquals(state.layers("c")[1].id, result) }
    }
    @Test fun donutLegendSelectsGroupValue() {
        compose.setContent { StorageTheme { DistributionChart("分类价值", listOf("食品" to 1000L, "日用品" to 3000L), currency = true) } }
        compose.onNodeWithContentDescription("分类价值，2 个分组，合计 ¥40.00").assertExists()
        compose.onNodeWithText("食品").performClick()
        compose.onNodeWithText("¥10.00").assertExists()
    }
    @Test fun chartDetailsUseSelectedGroupAndExplicitCountLabel() {
        var chosen = ""
        compose.setContent { StorageTheme { DistributionChart("库存", listOf("食品" to 3L, "衣物" to 2L), countLabel = "", onDetails = { chosen = it }) } }
        compose.onNodeWithText("食品").performClick()
        compose.onNodeWithText("查看选中明细").performClick()
        compose.runOnIdle { assertEquals("食品", chosen) }
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("3 个批次").assertDoesNotExist()
    }
    @Test fun oldItemUnitsAreNotDisplayedOnCards() {
        val state = InventoryRules.add(Inventory(), Thing("i", "牛奶", unit = "瓶"), Batch("b", "i"), 3, UNPLACED, "add")
        compose.setContent { StorageTheme { ItemCard(state, state.rows(), {}) } }
        compose.onNodeWithText("数量 3").assertExists()
        compose.onNodeWithText("3 瓶").assertDoesNotExist()
    }
    @Test fun emptyChartDisplaysExplanation() {
        compose.setContent { StorageTheme { DistributionChart("价值分布", emptyList(), currency = true) } }
        compose.onNodeWithText("暂无已知价值数据，填写购入单价后显示图表。").assertExists()
    }
    @Test fun cardOpensContainerAndDisplaysLayerCount() {
        var opened = false
        compose.setContent { StorageTheme { PlaceCard(Place("box", "room", "CONTAINER", "衣柜"), 8, 3,
            onClick = { opened = true }, onEdit = {}, onDelete = {}) } }
        compose.onNodeWithText("容器 · 3 层").assertExists()
        compose.onNodeWithText("8 种物品").assertExists()
        compose.onNodeWithText("衣柜").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
    @Test fun quantityColumnOpensItsCategory() {
        var chosen = ""
        compose.setContent { StorageTheme { QuantityColumns(listOf("食品" to 8L, "衣物" to 2L)) { chosen = it } } }
        compose.onNodeWithContentDescription("食品，库存数量 8，查看物品").performClick()
        compose.runOnIdle { assertEquals("食品", chosen) }
    }
    @Test fun roomBarsKeepDistinctLocationIds() {
        var chosen = ""
        compose.setContent { StorageTheme { RoomBars(listOf(RoomChartEntry("r1", "家一 / 客厅", 8), RoomChartEntry("r2", "家二 / 客厅", 2))) { chosen = it } } }
        compose.onNodeWithText("家二 / 客厅").performClick()
        compose.runOnIdle { assertEquals("r2", chosen) }
    }
    @Test fun expiryStatusOpensMatchingFilter() {
        var chosen = ""
        compose.setContent { StorageTheme { ExpiryStatusChart(mapOf("已过期" to 2, "即将到期" to 1)) { chosen = it } } }
        compose.onNodeWithText("即将到期").performClick()
        compose.runOnIdle { assertEquals("即将到期", chosen) }
        compose.onNodeWithText("未设置有效期").performClick()
        compose.runOnIdle { assertEquals("即将到期", chosen) }
    }
}
