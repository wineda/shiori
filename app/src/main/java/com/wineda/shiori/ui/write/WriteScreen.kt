package com.wineda.shiori.ui.write

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.BatonCard
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriScreen
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.monthDayJa

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
            BatonCard(state.baton, compact = true)
            JournalField("◯", "良かったこと", "今日うれしかったこと、うまくいったこと…", state.journal.good, ShioriColors.Good) { viewModel.onFieldChanged(good = it) }
            JournalField("◐", "しんどかったこと", "心が重かった瞬間、疲れたこと…", state.journal.hard, ShioriColors.Hard) { viewModel.onFieldChanged(hard = it) }
            JournalField("◑", "気付いたこと", "新しい発見、自分について分かったこと…", state.journal.insight, ShioriColors.Insight) { viewModel.onFieldChanged(insight = it) }
            JournalField("◉", "明日へのバトン", "明日の自分に伝えたいこと…", state.journal.tomorrow, ShioriColors.Tomorrow) { viewModel.onFieldChanged(tomorrow = it) }
            Spacer(Modifier.height(6.dp))
            Text(
                "── 書きたい項目だけでも大丈夫 ──",
                modifier = Modifier.fillMaxWidth(),
                color = ShioriColors.InkMute,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun JournalField(
    icon: String,
    label: String,
    placeholder: String,
    value: String,
    color: Color,
    onChange: (String) -> Unit,
) {
    ShioriCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = color)
            Text(label, color = ShioriColors.Ink, fontWeight = FontWeight.Medium)
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
