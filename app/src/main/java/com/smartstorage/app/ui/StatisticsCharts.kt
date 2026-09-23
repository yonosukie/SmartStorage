package com.smartstorage.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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


@Composable fun QuantityColumns(values: List<Pair<String, Long>>, onDetails: (String) -> Unit) {
    val entries = values.filter { it.second > 0 }.sortedByDescending { it.second }
    FormSection("分类库存数量", "看看哪些物品囤得多；点击柱形查看物品。") {
        if (entries.isEmpty()) Text("暂无统计数据。") else {
            Text("库存合计 ${entries.sumOf { it.second }}", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                entries.forEachIndexed { index, (name, value) ->
                    Column(Modifier.width(72.dp).clickable { onDetails(name) }
                        .semantics { contentDescription = "$name，库存数量 $value，查看物品" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(164.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(value.toString(), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.width(36.dp).height((128f * value.toFloat() / entries.maxOf { it.second }).coerceAtLeast(3f).dp)
                                    .background(chartColors[index % chartColors.size], RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (entries.size > 3) Text("左右滑动查看全部分类", style = MaterialTheme.typography.bodySmall)
        }
    }
}

data class RoomChartEntry(val id: String, val name: String, val quantity: Long)

@Composable fun RoomBars(values: List<RoomChartEntry>, onDetails: (String) -> Unit) {
    val entries = values.filter { it.quantity > 0 }.sortedByDescending { it.quantity }
    FormSection("房间收纳分布", "按库存数量比较；点击横条查看该位置的物品。") {
        if (entries.isEmpty()) Text("暂无统计数据。") else entries.forEachIndexed { index, entry ->
            Column(Modifier.fillMaxWidth().clickable { onDetails(entry.id) }.padding(vertical = 10.dp)
                .semantics { contentDescription = "${entry.name}，库存数量 ${entry.quantity}，查看物品" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(entry.quantity.toString(), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { entry.quantity.toFloat() / entries.maxOf { it.quantity } },
                    modifier = Modifier.fillMaxWidth().height(12.dp), color = chartColors[index % chartColors.size])
            }
        }
    }
}

@Composable fun ExpiryStatusChart(counts: Map<String, Int>, onDetails: (String) -> Unit) {
    val labels = listOf("已过期", "即将到期", "有效期内", "未设置")
    val colors = listOf(Color(0xFFB44F52), Color(0xFFAC751F), Color(0xFF287B65), Color(0xFF7D858A))
    val total = labels.sumOf { counts[it] ?: 0 }
    FormSection("到期提醒", "按在库批次统计，同一批次分放多处仅计一次。") {
        if (total == 0) Text("暂无在库物品，录入后可查看到期情况。") else {
            Row(Modifier.fillMaxWidth().height(24.dp)) {
                labels.forEachIndexed { index, label ->
                    val count = counts[label] ?: 0
                    if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(colors[index])
                        .clickable { onDetails(label) }.semantics { contentDescription = "$label，$count 个批次" })
                }
            }
            labels.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { label ->
                        val count = counts[label] ?: 0
                        val color = colors[labels.indexOf(label)]
                        Surface(Modifier.weight(1f), color = color.copy(alpha = .08f), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.clickable(enabled = count > 0) { onDetails(label) }.padding(12.dp)) {
                                Text(if (label == "未设置") "未设置有效期" else label, color = color, style = MaterialTheme.typography.labelLarge)
                                Text("$count 个批次", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            Text("点击状态查看清单；有效期内不含即将到期。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
