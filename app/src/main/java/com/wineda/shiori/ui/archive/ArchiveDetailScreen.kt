package com.wineda.shiori.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.EmptyState
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.jaDate

@Composable
fun ArchiveDetailScreen(onBack: () -> Unit, onEdit: () -> Unit, viewModel: ArchiveDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val journal = state.journal
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ShioriTopBar(journal?.date?.jaDate() ?: "記録", action = { androidx.compose.material3.TextButton(onClick = onEdit) { Text("編集") } })
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
            if (journal == null) EmptyState("記録が見つかりません。") else {
                DetailSection("◯ 良かったこと", journal.good)
                DetailSection("◐ しんどかったこと", journal.hard)
                DetailSection("◑ 気付いたこと", journal.insight)
                DetailSection("◉ 明日へのバトン", journal.tomorrow)
                ShioriCard {
                    Text("その日のメモ", color = ShioriColors.InkMute)
                    if (state.memos.isEmpty()) Text("メモはありません", color = ShioriColors.InkFaint)
                    state.memos.forEach { Text("${it.timeString} ${it.tag?.symbol ?: ""} ${it.text}") }
                }
            }
        }
    }
}

@Composable private fun DetailSection(title: String, body: String) = ShioriCard { Text(title); Text(body.ifBlank { "—" }, color = if (body.isBlank()) ShioriColors.InkFaint else ShioriColors.Ink) }
