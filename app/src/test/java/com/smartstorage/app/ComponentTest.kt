package com.smartstorage.app

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartstorage.app.ui.*
import com.smartstorage.core.Place
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
    @Test fun cardOpensContainerAndDisplaysLayerCount() {
        var opened = false
        compose.setContent { StorageTheme { PlaceCard(Place("box", "room", "CONTAINER", "衣柜"), 8, 3,
            onClick = { opened = true }, onEdit = {}, onDelete = {}) } }
        compose.onNodeWithText("容器 · 3 层").assertExists()
        compose.onNodeWithText("8 种物品").assertExists()
        compose.onNodeWithText("衣柜").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}
