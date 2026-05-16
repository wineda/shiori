package com.wineda.shiori.ui.write

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.wineda.shiori.ui.components.BatonCard
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriScreen
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.monthDayJa

private val BackfillAccent = Color(0xFF8B7D52)
private val BackfillWash = Color(0x2EB8AD8A)

@Composable
fun WriteScreen(onBack: () -> Unit, viewModel: WriteViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    ShioriScreen(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, ShioriColors.Divider)).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = ShioriColors.InkSoft)
                Text("戻る", color = ShioriColors.InkSoft)
            }
            Text(state.journal.date.monthDayJa(), color = ShioriColors.InkMute, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Button(
                onClick = { viewModel.saveNow(); onBack() },
                colors = ButtonDefaults.buttonColors(containerColor = ShioriColors.Good.copy(alpha = 0.35f), contentColor = ShioriColors.Ink),
                shape = RoundedCornerShape(50),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) { Text("保存") }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.isBackfillMode) {
                BackfillBanner(daysAgo = state.daysAgo)
            } else {
                BatonCard(state.baton, compact = true)
            }
            JournalField("◯", "良かったこと", "今日うれしかったこと、うまくいったこと…", state.journal.good, ShioriColors.Good) { viewModel.onFieldChanged(good = it) }
            JournalField("◐", "しんどかったこと", "心が重かった瞬間、疲れたこと…", state.journal.hard, ShioriColors.Hard) { viewModel.onFieldChanged(hard = it) }
            JournalField("◑", "気付いたこと", "新しい発見、自分について分かったこと…", state.journal.insight, ShioriColors.Insight) { viewModel.onFieldChanged(insight = it) }
            JournalField(
                icon = "◉",
                label = "明日へのバトン",
                placeholder = if (state.isBackfillMode) "あの日の自分が、次の日に何を託したか…" else "明日の自分に伝えたいこと…",
                value = state.journal.tomorrow,
                color = ShioriColors.Tomorrow,
                tag = if (state.isBackfillMode) "当時の意図" else null,
            ) { viewModel.onFieldChanged(tomorrow = it) }
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.isBackfillMode) "── 空欄のままでも保存できます ──" else "── 書きたい項目だけでも大丈夫 ──",
                modifier = Modifier.fillMaxWidth(),
                color = ShioriColors.InkMute,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BackfillBanner(daysAgo: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BackfillWash)
            .border(1.dp, Color(0x4DB8AD8A), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33B8AD8A)).padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = BackfillAccent)
            }
            SectionLabel("BACKFILL · ${daysAgo}日前")
        }
        Text(
            "${daysAgo}日前を振り返って記録しています。覚えている範囲で大丈夫。",
            color = ShioriColors.InkSoft,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun JournalField(
    icon: String,
    label: String,
    placeholder: String,
    value: String,
    color: Color,
    tag: String? = null,
    onChange: (String) -> Unit,
) {
    ShioriCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = color)
            Text(label, color = ShioriColors.Ink, fontWeight = FontWeight.Medium)
            if (tag != null) {
                Text(
                    text = tag,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x26B8AD8A)).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = BackfillAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text(placeholder, color = ShioriColors.InkFaint) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = color,
            ),
        )
    }
}
