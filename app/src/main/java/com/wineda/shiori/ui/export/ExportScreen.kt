package com.wineda.shiori.ui.export

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.PrimaryShioriButton
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriScreen
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.components.TagPill
import com.wineda.shiori.ui.theme.ShioriColors
import java.io.File

@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    ShioriScreen(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 22.dp)) {
        ShioriTopBar(title = "解析にかける", eyebrow = "EXPORT")
        Text(
            text = "ChatGPT等のAIに貼り付けて、\n自分のパターンや気付きを見つけよう。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            color = ShioriColors.InkSoft,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("期間")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportRange.entries.forEach { range ->
                        TagPill(range.label, ShioriColors.Ink, state.range == range) { viewModel.selectRange(range) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("解析プロンプト")
                PromptPreset.entries.forEach { preset ->
                    PromptRow(preset, state.promptPreset == preset) { viewModel.selectPrompt(preset) }
                }
                if (state.promptPreset == PromptPreset.CUSTOM) {
                    OutlinedTextField(state.customPrompt, viewModel::updateCustomPrompt, modifier = Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(10.dp))
                }
            }
            OptionRow("メモを含める", state.includeMemos, viewModel::toggleMemos)
            OptionRow("プロンプトを冒頭に含める", state.includePrompt, viewModel::togglePrompt)
            PrimaryShioriButton(if (state.isGenerating) "生成中…" else "Markdownを生成", enabled = !state.isGenerating, onClick = viewModel::generate)
            if (state.markdown.isNotBlank()) {
                ShioriCard {
                    Text("プレビュー", color = ShioriColors.Ink, fontWeight = FontWeight.Medium)
                    Text(state.markdown, modifier = Modifier.heightIn(max = 360.dp), color = ShioriColors.InkSoft, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    PrimaryShioriButton(".md ファイルで共有", onClick = { shareMarkdown(context, state.markdown, "shiori-journal.md") })
                }
            }
        }
    }
}

@Composable
private fun PromptRow(preset: PromptPreset, selected: Boolean, onClick: () -> Unit) {
    ShioriCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(preset.titleJa(), color = ShioriColors.Ink, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(preset.descriptionJa(), color = ShioriColors.InkMute, fontSize = 10.sp)
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = ShioriColors.Good)
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, color = ShioriColors.InkSoft)
    }
}

private fun PromptPreset.titleJa() = when (this) {
    PromptPreset.PATTERNS -> "パターンを見つける"
    PromptPreset.HIDDEN -> "隠れた気付き"
    PromptPreset.BATON -> "バトンの達成度"
    PromptPreset.CUSTOM -> "カスタム"
}

private fun PromptPreset.descriptionJa() = when (this) {
    PromptPreset.PATTERNS -> "繰り返し出てくる感情や思考の傾向"
    PromptPreset.HIDDEN -> "本人が見落としている発見の抽出"
    PromptPreset.BATON -> "明日への意図がどう実現されたか"
    PromptPreset.CUSTOM -> "自由にプロンプトを書く"
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
