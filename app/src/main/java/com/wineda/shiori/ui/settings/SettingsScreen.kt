package com.wineda.shiori.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriScreen
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.theme.ShioriColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadBackup(uri)
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onWritePermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                SettingsEvent.RequestWritePermission -> writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                SettingsEvent.OpenBackupPicker -> openBackup.launch(arrayOf("application/json"))
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ShioriColors.Paper,
    ) { padding ->
        ShioriScreen(Modifier.fillMaxSize().padding(padding)) {
            ShioriTopBar(
                title = "設定",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = ShioriColors.InkSoft)
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SectionLabel("SETTINGS")
                SettingsSectionHeader("── データ ──")
                SettingsActionCard(
                    icon = Icons.Filled.FileDownload,
                    title = "バックアップを作成",
                    subtitle = "手動で現在のデータをJSON形式で保存",
                    enabled = !state.isWorking,
                    onClick = viewModel::createBackupFromUserAction,
                )
                SettingsActionCard(
                    icon = Icons.Filled.FileUpload,
                    title = "バックアップから復元",
                    subtitle = "保存済みファイルからデータを戻す",
                    enabled = !state.isWorking,
                    onClick = viewModel::restoreFromUserAction,
                )

                SettingsSectionHeader("── 情報 ──")
                SettingsInfo(label = "バックアップ先", value = "端末内 / Documents / Shiori")
                SettingsInfo(label = "最終バックアップ", value = state.lastBackupText)
            }

            if (state.isWorking) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ShioriColors.InkSoft, strokeWidth = 2.dp)
                }
            }
        }
    }

    state.pendingRestore?.let { pending ->
        RestoreConfirmDialog(
            pending = pending,
            onDismiss = viewModel::dismissRestoreDialog,
            onConfirm = viewModel::confirmRestore,
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(text, color = ShioriColors.InkMute, fontSize = 10.sp, letterSpacing = 2.4.sp)
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0x268B8278)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ShioriColors.Good.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = ShioriColors.InkSoft, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = ShioriColors.Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, color = ShioriColors.InkMute, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SettingsInfo(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = ShioriColors.InkMute, fontSize = 10.sp)
        Text(value, color = Color(0xFF3A3530), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RestoreConfirmDialog(
    pending: PendingRestoreUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("復元の確認", color = ShioriColors.Ink, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("以下のデータを読み込みます:", color = ShioriColors.InkSoft)
                Text("・ジャーナル: ${pending.journalCount}件", color = ShioriColors.InkSoft)
                Text("・メモ: ${pending.memoCount}件", color = ShioriColors.InkSoft)
                Spacer(Modifier.height(4.dp))
                Text("バックアップ日時:", color = ShioriColors.InkMute, fontSize = 10.sp)
                Text(pending.exportedAtText, color = ShioriColors.InkSoft)
                Spacer(Modifier.height(4.dp))
                Text("現在のデータは置き換えられます。\n（自動的に現状のバックアップを作成してから復元します）", color = ShioriColors.InkSoft)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("復元する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        containerColor = ShioriColors.Card,
    )
}
