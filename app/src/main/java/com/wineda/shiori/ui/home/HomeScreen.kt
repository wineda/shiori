package com.wineda.shiori.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.BatonCard
import com.wineda.shiori.ui.components.IconCircle
import com.wineda.shiori.ui.components.PrimaryShioriButton
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriScreen
import com.wineda.shiori.ui.components.StreakIndicator
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.jaDate

@Composable
fun HomeScreen(
    onWrite: () -> Unit,
    onMemo: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ShioriScreen(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    SectionLabel("SHIORI · 栞")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "昨日と明日を、\n今日で結ぶ。",
                        color = ShioriColors.Ink,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定", tint = ShioriColors.InkSoft)
                    }
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Filled.EventNote, contentDescription = "記録", tint = ShioriColors.InkSoft)
                    }
                }
            }

            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ShioriCard {
                    SectionLabel("TODAY")
                    Text(state.today.jaDate(), color = ShioriColors.Ink, style = MaterialTheme.typography.bodyLarge)
                }

                ShioriCard(Modifier.clickable(onClick = onMemo)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(Icons.Filled.Message)
                            Column {
                                SectionLabel("TODAY'S MEMO")
                                Text("今日のメモ · ${state.memoCount}件", color = ShioriColors.Ink, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ShioriColors.InkFaint)
                    }
                }

                BatonCard(state.baton)
                StreakIndicator(state.streak)
            }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryShioriButton("今日を書く", onClick = onWrite)
            Text(
                text = "5項目 · 平均 4分",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = ShioriColors.InkMute,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
