package com.wineda.shiori.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.domain.model.Journal
import com.wineda.shiori.ui.components.EmptyState
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriScreen
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.monthDayJa
import kotlinx.datetime.LocalDate

@Composable
fun ArchiveScreen(
    onExport: () -> Unit,
    onCalendar: () -> Unit,
    onDetail: (String) -> Unit,
    onBackfill: (String) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ShioriScreen(Modifier.fillMaxSize()) {
        ShioriTopBar(
            title = "これまでの記録",
            eyebrow = "ARCHIVE",
            action = {
                Row {
                    IconButton(onClick = onCalendar) { Icon(Icons.Filled.Add, contentDescription = "過去日を追加", tint = ShioriColors.InkSoft) }
                    IconButton(onClick = onExport) { Icon(Icons.Filled.Share, contentDescription = "共有", tint = ShioriColors.InkSoft) }
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousMonth) { Icon(Icons.Filled.ChevronLeft, contentDescription = "前月", tint = ShioriColors.InkMute) }
            Text("${state.month.year}年 ${state.month.monthNumber}月", color = ShioriColors.Ink, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            IconButton(onClick = viewModel::nextMonth) { Icon(Icons.Filled.ChevronRight, contentDescription = "翌月", tint = ShioriColors.InkMute) }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("連続記録", "${state.currentStreak}", "日", Modifier.weight(1f))
            StatCard("今月の記録", "${state.monthlyCount}", "件", Modifier.weight(1f))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.timelineItems.isEmpty()) item { EmptyState("この月の記録はまだありません。") }
            items(state.timelineItems, key = { it.date.toString() }) { item ->
                when (item) {
                    is ArchiveTimelineItem.Entry -> JournalRow(item.journal) { onDetail(item.journal.date.toString()) }
                    is ArchiveTimelineItem.Gap -> BackfillGapRow(item.date) { onBackfill(item.date.toString()) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, modifier: Modifier) {
    ShioriCard(modifier) {
        Text(label, color = ShioriColors.InkMute, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = ShioriColors.Ink, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(unit, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp), color = ShioriColors.InkMute, fontSize = 12.sp)
        }
    }
}

@Composable
private fun JournalRow(journal: Journal, onClick: () -> Unit) {
    ShioriCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(journal.date.monthDayJa(), color = ShioriColors.InkMute, fontSize = 12.sp)
                if (journal.isBackfilled) SectionLabel("BACKFILL")
            }
            Text(journal.symbol(), color = journal.symbolColor(), fontSize = 13.sp)
        }
        Text(journal.preview(), color = ShioriColors.InkSoft, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BackfillGapRow(date: LocalDate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Transparent)
            .dashedBorder(Color(0x668B8278))
            .clickable(onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x33B8AD8A)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = ShioriColors.InkFaint, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(date.monthDayJa(), color = ShioriColors.InkMute, fontSize = 12.sp)
            Text("この日を遡って書く", color = ShioriColors.InkFaint, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
    drawRoundRect(
        color = color,
        size = size,
        cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
        style = stroke,
    )
}

private fun Journal.preview() = listOf(good, hard, insight, tomorrow).firstOrNull { it.isNotBlank() }?.take(80) ?: "空の記録"
private fun Journal.symbol() = when {
    good.isNotBlank() -> "◯"
    hard.isNotBlank() -> "◐"
    insight.isNotBlank() -> "◑"
    tomorrow.isNotBlank() -> "◉"
    else -> "○"
}
private fun Journal.symbolColor() = when {
    good.isNotBlank() -> ShioriColors.Good
    hard.isNotBlank() -> ShioriColors.Hard
    insight.isNotBlank() -> ShioriColors.Insight
    tomorrow.isNotBlank() -> ShioriColors.Tomorrow
    else -> ShioriColors.InkFaint
}
