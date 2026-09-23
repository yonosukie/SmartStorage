package com.smartstorage.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartstorage.core.*

private val chartColors = listOf(Color(0xFF287B65), Color(0xFFCC934E), Color(0xFF657CC2), Color(0xFFBB6888), Color(0xFF71A8AB), Color(0xFF9D855E), Color(0xFF88928B))

@Composable fun DistributionChart(title: String, values: List<Pair<String, Long>>, currency: Boolean = false, donut: Boolean = true,
    countLabel: String = "个批次", subtitle: String? = null, preserveOrder: Boolean = false, groupLimit: Int = 6, onDetails: ((String) -> Unit)? = null) {
    val positive = values.filter { it.second > 0 }
    val entries = if (preserveOrder) positive else positive.sortedByDescending { it.second }
    val slices = if (groupLimit > 0 && entries.size > groupLimit) entries.take(groupLimit) + ("其他（${entries.size - groupLimit} 项）" to entries.drop(groupLimit).sumOf { it.second }) else entries
    val total = slices.sumOf { it.second }
    var selected by remember(values) { mutableStateOf<String?>(null) }
    fun label(value: Long) = if (currency) "¥${money(value)}" else "$value $countLabel".trim()
    FormSection(title, subtitle) {
        if (total == 0L) {
            val track = MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(136.dp)) { drawCircle(track, style = Stroke(18.dp.toPx())) }
                Text(label(0), style = MaterialTheme.typography.titleLarge)
            }
            Text(if (currency) "暂无已知价值数据，填写购入单价后显示图表。" else "暂无统计数据。")
        }
        else {
            if (donut) Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(184.dp).semantics { contentDescription = "$title，${slices.size} 个分组，合计 ${label(total)}" }) {
                    val stroke = 26.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    var angle = -90f
                    slices.forEachIndexed { index, (name, value) ->
                        val sweep = (value.toDouble() / total * 360).toFloat()
                        drawArc(chartColors[index % chartColors.size].copy(alpha = if (selected == null || selected == name) 1f else .3f),
                            angle, sweep, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke))
                        angle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (selected == null) "合计" else selected!!, style = MaterialTheme.typography.labelMedium)
                    Text(label(slices.firstOrNull { it.first == selected }?.second ?: total), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (!donut) Text("合计 ${label(total)}", style = MaterialTheme.typography.titleMedium)
            slices.forEachIndexed { index, (name, value) ->
                val ratio = value.toDouble() / total
                TextButton({ selected = if (selected == name) null else name }, Modifier.fillMaxWidth()) {
                    Box(Modifier.size(10.dp).background(chartColors[index % chartColors.size], CircleShape))
                    Text(name, Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("${label(value)} · ${"%.1f".format(ratio * 100)}%")
                }
                if (!donut) LinearProgressIndicator(progress = { (value.toDouble() / slices.maxOf { it.second }).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp), color = chartColors[index % chartColors.size])
            }
            if (onDetails != null && selected != null && entries.any { it.first == selected }) {
                TextButton({ onDetails(selected!!) }, Modifier.align(Alignment.End)) { Text("查看选中明细") }
            }
            Text(if (donut) "点击图例查看分组数值" else "横条按分组最大值缩放；百分比按图中分组总和计算", style = MaterialTheme.typography.bodySmall)
        }
    }
}
