package com.wineda.shiori.ui.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.domain.model.Memo
import com.wineda.shiori.domain.model.MemoTag
import com.wineda.shiori.ui.components.EmptyState
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.components.TagPill
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.monthDayJa

@Composable
fun MemoScreen(viewModel: MemoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ShioriTopBar("今日のメモ", "${state.memos.size} 件")
        Text(state.today.monthDayJa(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = ShioriColors.InkMute)
        LazyColumn(modifier = Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.memos.isEmpty()) item { EmptyState("いま浮かんだことを、短く残せます。") }
            items(state.memos, key = { it.id }) { MemoBubble(it) }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoTag.entries.forEach { tag -> TagPill("${tag.symbol} ${tag.label}", tag.color(), state.selectedTag == tag) { viewModel.onTagSelected(tag) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.input, onValueChange = viewModel::onInputChanged, placeholder = { Text("いま、なにか浮かんでる?") }, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::send, enabled = state.input.isNotBlank()) { Icon(Icons.Filled.Send, contentDescription = "送信") }
            }
        }
    }
}

@Composable
private fun MemoBubble(memo: Memo) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(Modifier.fillMaxWidth(0.86f).clip(RoundedCornerShape(20.dp)).background(ShioriColors.Card).padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(memo.timeString, color = ShioriColors.InkMute)
                memo.tag?.let { Text("${it.symbol} ${it.label}", color = it.color()) }
            }
            Text(memo.text)
        }
    }
}

private fun MemoTag.color() = when (this) {
    MemoTag.GOOD -> ShioriColors.Good
    MemoTag.HARD -> ShioriColors.Hard
    MemoTag.INSIGHT -> ShioriColors.Insight
    MemoTag.TOMORROW -> ShioriColors.Tomorrow
}
