package com.wineda.shiori.ui.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.BatonCard
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.jaDate

@Composable
fun WriteScreen(onBack: () -> Unit, viewModel: WriteViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ShioriTopBar(
            title = state.journal.date.jaDate(),
            subtitle = if (state.saved) "保存済み" else "自動保存待ち…",
            action = { TextButton(onClick = { viewModel.saveNow(); onBack() }) { Text("保存") } },
        )
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
            BatonCard(state.baton)
            JournalField("◯ 良かったこと", state.journal.good, ShioriColors.Good) { viewModel.onFieldChanged(good = it) }
            JournalField("◐ しんどかったこと", state.journal.hard, ShioriColors.Hard) { viewModel.onFieldChanged(hard = it) }
            JournalField("◑ 気付いたこと", state.journal.insight, ShioriColors.Insight) { viewModel.onFieldChanged(insight = it) }
            JournalField("◉ 明日へのバトン", state.journal.tomorrow, ShioriColors.Tomorrow) { viewModel.onFieldChanged(tomorrow = it) }
            Text("書きたい項目だけでも大丈夫", color = ShioriColors.InkMute)
        }
    }
}

@Composable
private fun JournalField(label: String, value: String, color: androidx.compose.ui.graphics.Color, onChange: (String) -> Unit) {
    ShioriCard {
        Text(label, color = color)
        OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("ここに書く") })
    }
}
