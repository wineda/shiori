package com.wineda.shiori.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriScreen
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.monthDayJa
import kotlinx.datetime.LocalDate

@Composable
fun CalendarPickerScreen(
    onBack: () -> Unit,
    onWritePast: (String) -> Unit,
    viewModel: CalendarPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ShioriScreen(Modifier.fillMaxSize()) {
        ShioriTopBar(
            title = "日付を選ぶ",
            eyebrow = "BACKFILL",
            leading = { IconButton(onClick = onBack) { Icon(Icons.Filled.ChevronLeft, contentDescription = "戻る", tint = ShioriColors.InkSoft) } },
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousMonth) { Icon(Icons.Filled.ChevronLeft, contentDescription = "前月", tint = ShioriColors.InkMute) }
                Text("${state.month.monthNumber}月", color = ShioriColors.Ink, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                IconButton(onClick = viewModel::nextMonth) { Icon(Icons.Filled.ChevronRight, contentDescription = "翌月", tint = ShioriColors.InkMute) }
            }

            ShioriCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("日", "月", "火", "水", "木", "金", "土").forEach { label ->
                        Text(label, modifier = Modifier.weight(1f), color = ShioriColors.InkMute, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val cells = List(state.leadingBlanks) { null } + state.days.map { it }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                DayCell(day, selected = state.selectedDate == day.date, modifier = Modifier.weight(1f)) {
                                    viewModel.select(day.date)
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot()
                Text("記録あり", color = ShioriColors.InkMute, fontSize = 12.sp)
                LegendBox()
                Text("空き", color = ShioriColors.InkMute, fontSize = 12.sp)
            }

            state.selectedDate?.let { date ->
                ConfirmationPanel(date = date) { onWritePast(date.toString()) }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val enabled = day.isPastEmpty
    val background = when {
        selected -> ShioriColors.Ink
        day.hasEntry -> ShioriColors.Good.copy(alpha = 0.15f)
        day.isPastEmpty -> ShioriColors.Card
        else -> Color.Transparent
    }
    val content = if (selected) ShioriColors.Paper else ShioriColors.Ink
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .then(if (day.isPastEmpty) Modifier.border(1.dp, Color(0x268B8278), RoundedCornerShape(16.dp)) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("${day.date.dayOfMonth}", color = content.copy(alpha = if (day.isFuture) 0.3f else 1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (day.hasEntry) Text("●", color = ShioriColors.Good, fontSize = 9.sp)
        }
        if (day.isToday) {
            Text("今", modifier = Modifier.align(Alignment.TopEnd), color = Color(0xFFB8AD8A), fontSize = 9.sp)
        }
    }
}

@Composable
private fun ConfirmationPanel(date: LocalDate, onConfirm: () -> Unit) {
    ShioriCard {
        SectionLabel("SELECTED")
        Text(date.monthDayJa(), color = ShioriColors.Ink, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShioriColors.Ink, contentColor = ShioriColors.Paper),
            shape = RoundedCornerShape(50),
        ) { Text("この日の記録を書く") }
    }
}

@Composable
private fun LegendDot() {
    Text("●", color = ShioriColors.Good, fontSize = 12.sp)
}

@Composable
private fun LegendBox() {
    Box(Modifier.size(12.dp).border(1.dp, Color(0x268B8278), RoundedCornerShape(3.dp)).background(ShioriColors.Card))
}
