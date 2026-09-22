package com.smartstorage.core

import kotlin.math.roundToInt

/** One grid shared by the canvas, drag snapping, resizing and persisted rectangles. */
object LayoutGrid {
    const val CELLS = 10
    fun snap(box: LayoutBox): LayoutBox {
        require(listOf(box.x, box.y, box.width, box.height).all { it.isFinite() })
        val width = (box.width * CELLS).roundToInt().coerceIn(1, CELLS)
        val height = (box.height * CELLS).roundToInt().coerceIn(1, CELLS)
        val x = (box.x * CELLS).roundToInt().coerceIn(0, CELLS - width)
        val y = (box.y * CELLS).roundToInt().coerceIn(0, CELLS - height)
        return box.copy(x = x.toFloat() / CELLS, y = y.toFloat() / CELLS,
            width = width.toFloat() / CELLS, height = height.toFloat() / CELLS)
    }
}
