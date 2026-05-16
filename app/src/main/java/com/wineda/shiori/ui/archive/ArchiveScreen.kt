package com.wineda.shiori.ui.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.domain.model.Journal
import com.wineda.shiori.ui.components.EmptyState
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.jaDate

@Composable
fun ArchiveScreen(onExport: () -> Unit, onDetail: (String) -> Unit, viewModel: ArchiveViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ShioriTopBar("これまでの記録", action = { IconButton(onClick = onExport) { Icon(Icons.Filled.IosShare, contentDescription = "共有") } })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousMonth) { Icon(Icons.Filled.ChevronLeft, contentDescription = "前月") }
            Text("${state.month.year}年 ${state.month.monthNumber}月", fontWeight = FontWeight.Medium)
            IconButton(onClick = viewModel::nextMonth) { Icon(Icons.Filled.ChevronRight, contentDescription = "翌月") }
        }
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShioriCard(Modifier.weight(1f)) { Text("連続記録"); Text("${state.currentStreak}日") }
            ShioriCard(Modifier.weight(1f)) { Text("今月"); Text("${state.monthlyCount}件") }
        }
        LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.journals.isEmpty()) item { EmptyState("この月の記録はまだありません。") }
            items(state.journals, key = { it.date.toString() }) { journal -> JournalRow(journal) { onDetail(journal.date.toString()) } }
        }
    }
}

@Composable
private fun JournalRow(journal: Journal, onClick: () -> Unit) {
    ShioriCard(Modifier.clickable(onClick = onClick)) {
        Text(journal.date.jaDate(), fontWeight = FontWeight.Medium)
        Text(journal.preview(), color = ShioriColors.InkSoft)
        Text(journal.symbol(), color = ShioriColors.Accent)
    }
}

private fun Journal.preview() = listOf(good, hard, insight, tomorrow).firstOrNull { it.isNotBlank() }?.take(80) ?: "空の記録"
private fun Journal.symbol() = when {
    good.isNotBlank() -> "◯"
    hard.isNotBlank() -> "◐"
    insight.isNotBlank() -> "◑"
    tomorrow.isNotBlank() -> "◉"
    else -> "○"
}
