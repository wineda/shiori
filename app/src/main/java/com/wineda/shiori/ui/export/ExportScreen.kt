package com.wineda.shiori.ui.export

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.components.TagPill
import com.wineda.shiori.ui.theme.ShioriColors
import java.io.File

@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ShioriTopBar("解析エクスポート", "Markdownで書き出し")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ShioriCard {
                Text("期間")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ExportRange.entries.forEach { TagPill(it.label, ShioriColors.Tomorrow, state.range == it) { viewModel.selectRange(it) } } }
            }
            ShioriCard {
                Text("プロンプト")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { PromptPreset.entries.forEach { TagPill(it.label, ShioriColors.Insight, state.promptPreset == it) { viewModel.selectPrompt(it) } } }
                if (state.promptPreset == PromptPreset.CUSTOM) OutlinedTextField(state.customPrompt, viewModel::updateCustomPrompt, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
            OptionRow("メモを含める", state.includeMemos, viewModel::toggleMemos)
            OptionRow("プロンプトを冒頭に含める", state.includePrompt, viewModel::togglePrompt)
            Button(onClick = viewModel::generate, enabled = !state.isGenerating) { Text(if (state.isGenerating) "生成中…" else "Markdownを生成") }
            if (state.markdown.isNotBlank()) {
                ShioriCard {
                    Text("プレビュー")
                    Text(state.markdown, modifier = Modifier.heightIn(max = 420.dp))
                    Button(onClick = { shareMarkdown(context, state.markdown, "shiori-journal.md") }) { Text("共有") }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label)
    }
}

fun shareMarkdown(context: Context, markdown: String, filename: String) {
    val cacheFile = File(context.cacheDir, filename).apply { writeText(markdown) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "栞 ジャーナル")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "共有する"))
}
