package com.wineda.shiori.ui.memo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.domain.model.Memo
import com.wineda.shiori.domain.model.MemoTag
import com.wineda.shiori.ui.components.EmptyState
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriScreen
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.components.TagPill
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.monthDayJa

@Composable
fun MemoScreen(viewModel: MemoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    ShioriScreen(Modifier.fillMaxSize()) {
        ShioriTopBar(title = "今日のメモ", eyebrow = "MEMO", action = { Text("${state.memos.size}件", color = ShioriColors.InkMute, fontSize = 10.sp) })
        Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                state.today.monthDayJa(),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(ShioriColors.Divider).padding(horizontal = 12.dp, vertical = 4.dp),
                color = ShioriColors.InkSoft,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.memos.isEmpty()) item { EmptyState("ふと浮かんだことを、\nここに置いていく") }
            items(state.memos, key = { it.id }) { MemoBubble(it) }
        }
        TagSelector(selectedTag = state.selectedTag, onSelect = viewModel::onTagSelected)
        Row(
            modifier = Modifier.fillMaxWidth().background(ShioriColors.Card).border(BorderStroke(1.dp, ShioriColors.Divider)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChanged,
                placeholder = { Text("いま、なにか浮かんでる?", color = ShioriColors.InkFaint) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                minLines = 1,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ShioriColors.PaperDeep,
                    unfocusedContainerColor = ShioriColors.PaperDeep,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = ShioriColors.Ink,
                ),
            )
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(if (state.input.isNotBlank()) ShioriColors.Ink else ShioriColors.Divider),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = viewModel::send, enabled = state.input.isNotBlank()) {
                    Icon(Icons.Filled.Send, contentDescription = "送信", tint = if (state.input.isNotBlank()) ShioriColors.Paper else ShioriColors.InkFaint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MemoBubble(memo: Memo) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier
                .widthIn(max = 310.dp)
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(ShioriColors.Card)
                .border(BorderStroke(1.dp, ShioriColors.Divider), RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            memo.tag?.let { tag ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tag.symbol, color = tag.color(), fontSize = 10.sp)
                    Text(tag.label, color = tag.color(), fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
            Text(memo.text, color = ShioriColors.Ink, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        }
        Text(memo.timeString, modifier = Modifier.padding(top = 3.dp, end = 6.dp), color = ShioriColors.InkFaint, fontSize = 10.sp)
    }
}

@Composable
private fun TagSelector(selectedTag: MemoTag?, onSelect: (MemoTag?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ShioriColors.Card.copy(alpha = 0.5f)).border(BorderStroke(1.dp, ShioriColors.Divider)).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Label, contentDescription = null, tint = ShioriColors.InkFaint, modifier = Modifier.size(14.dp))
        MemoTag.entries.forEach { tag -> TagPill("${tag.symbol} ${tag.label}", tag.color(), selectedTag == tag) { onSelect(tag) } }
    }
}

private fun MemoTag.color() = when (this) {
    MemoTag.GOOD -> ShioriColors.Good
    MemoTag.HARD -> ShioriColors.Hard
    MemoTag.INSIGHT -> ShioriColors.Insight
    MemoTag.TOMORROW -> ShioriColors.Tomorrow
}
