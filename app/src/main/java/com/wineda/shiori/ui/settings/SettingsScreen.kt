package com.wineda.shiori.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineda.shiori.ui.components.SectionLabel
import com.wineda.shiori.ui.components.ShioriScreenBrush
import com.wineda.shiori.ui.components.ShioriTopBar
import com.wineda.shiori.ui.theme.ShioriColors

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readRestoreFile)
    }
    val requestWritePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.createBackup()
        } else {
            viewModel.showMessage("書き込み権限がないためバックアップを作成できません")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    state.pendingRestore?.let { backup ->
        RestoreConfirmDialog(
            journalCount = backup.journals.size,
            memoCount = backup.memos.size,
            exportedAt = backup.exportedAt.toJapaneseDateTime(),
            onDismiss = viewModel::cancelRestore,
            onConfirm = viewModel::confirmRestore,
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = Color.Transparent) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ShioriScreenBrush)
                .padding(paddingValues),
        ) {
            ShioriTopBar(
                title = "設定",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = ShioriColors.InkSoft)
                    }
                },
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SectionLabel("SETTINGS")
                SettingsSectionHeader("── データ ──")
                SettingsActionCard(
                    icon = Icons.Filled.FileDownload,
                    title = "バックアップを作成",
                    subtitle = "手動で現在のデータを保存",
                    enabled = !state.isBusy,
                ) {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        viewModel.createBackup()
                    }
                }
                SettingsActionCard(
                    icon = Icons.Filled.FileUpload,
                    title = "バックアップから復元",
                    subtitle = "保存済みファイルから戻す",
                    enabled = !state.isBusy,
                ) { openDocument.launch(arrayOf("application/json")) }

                SettingsSectionHeader("── 情報 ──")
                InfoBlock("バックアップ先", "端末内 / Documents / Shiori")
                InfoBlock("最終バックアップ", state.lastBackupAt)
                if (state.isBusy) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = ShioriColors.InkSoft, strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(text, color = ShioriColors.InkMute, fontSize = 10.sp, letterSpacing = 2.4.sp)
}

@Composable
private fun SettingsActionCard(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ShioriColors.Card)
            .border(1.dp, ShioriColors.Divider, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(ShioriColors.Good.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ShioriColors.InkSoft, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = ShioriColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ShioriColors.InkMute, fontSize = 10.sp)
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = ShioriColors.InkMute, fontSize = 10.sp)
        Text(value, color = Color(0xFF3A3530), fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RestoreConfirmDialog(journalCount: Int, memoCount: Int, exportedAt: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("復元の確認", color = ShioriColors.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("以下のデータを読み込みます:")
                Text("・ジャーナル: ${journalCount}件")
                Text("・メモ: ${memoCount}件")
                Spacer(Modifier.height(4.dp))
                Text("バックアップ日時:")
                Text(exportedAt, color = ShioriColors.InkSoft)
                Spacer(Modifier.height(4.dp))
                Text("現在のデータは置き換えられます。\n(自動的に現状のバックアップを作成してから復元します)")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ShioriColors.Ink, contentColor = ShioriColors.Paper),
            ) { Text("復元する") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        containerColor = ShioriColors.Card,
    )
}
