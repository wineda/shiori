package com.wineda.shiori.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.BatonCard
import com.wineda.shiori.ui.components.ShioriCard
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.components.StreakIndicator
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.util.jaDate

@Composable
fun HomeScreen(onWrite: () -> Unit, onMemo: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ShioriTopBar("SHIORI · 栞", "昨日と明日を、今日で結ぶ。")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ShioriCard {
                Text("TODAY", color = ShioriColors.InkMute, style = MaterialTheme.typography.labelSmall)
                Text(state.today.jaDate(), style = MaterialTheme.typography.displayLarge)
                Text("書きたい項目だけでも大丈夫。", color = ShioriColors.InkSoft)
            }
            ShioriCard(modifier = Modifier) {
                Text("今日のメモ", color = ShioriColors.InkMute, style = MaterialTheme.typography.labelSmall)
                Text("${state.memoCount} 件", fontWeight = FontWeight.Medium)
                Button(onClick = onMemo) { Text("メモを開く") }
            }
            BatonCard(state.baton)
            ShioriCard {
                Text("過去7日間", color = ShioriColors.InkMute, style = MaterialTheme.typography.labelSmall)
                StreakIndicator(state.streak)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onWrite, modifier = Modifier.height(56.dp)) { Text("今日を書く") }
        }
    }
}
