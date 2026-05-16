package com.wineda.shiori.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineda.shiori.data.backup.BackupData
import com.wineda.shiori.domain.usecase.CreateBackupUseCase
import com.wineda.shiori.domain.usecase.GetLastBackupInfoUseCase
import com.wineda.shiori.domain.usecase.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val lastBackupAt: String = "未実施",
    val isBusy: Boolean = false,
    val pendingRestore: BackupData? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    getLastBackupInfo: GetLastBackupInfoUseCase,
) : ViewModel() {
    private val internalState = MutableStateFlow(SettingsUiState())
    val uiState = combine(internalState, getLastBackupInfo()) { state, lastBackupAt ->
        state.copy(lastBackupAt = lastBackupAt?.toJapaneseDateTime() ?: "未実施")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun createBackup() = viewModelScope.launch {
        internalState.update { it.copy(isBusy = true) }
        runCatching { createBackupUseCase() }
            .onSuccess { result -> _messages.send("バックアップを作成しました: ${result.fileName}") }
            .onFailure { throwable -> _messages.send("バックアップに失敗しました: ${throwable.message ?: "不明なエラー"}") }
        internalState.update { it.copy(isBusy = false) }
    }

    fun readRestoreFile(uri: Uri) = viewModelScope.launch {
        internalState.update { it.copy(isBusy = true) }
        runCatching { restoreBackup.read(uri) }
            .onSuccess { backup -> internalState.update { it.copy(pendingRestore = backup) } }
            .onFailure { throwable -> _messages.send(throwable.message ?: "ファイルが読めません") }
        internalState.update { it.copy(isBusy = false) }
    }

    fun showMessage(message: String) = viewModelScope.launch {
        _messages.send(message)
    }

    fun cancelRestore() {
        internalState.update { it.copy(pendingRestore = null) }
    }

    fun confirmRestore() = viewModelScope.launch {
        val backup = internalState.value.pendingRestore ?: return@launch
        internalState.update { it.copy(isBusy = true) }
        runCatching { restoreBackup.restore(backup) }
            .onSuccess { result ->
                internalState.update { it.copy(pendingRestore = null) }
                _messages.send("復元しました(ジャーナル ${result.journalCount} 件・メモ ${result.memoCount} 件)")
            }
            .onFailure { throwable -> _messages.send(throwable.message ?: "復元に失敗しました") }
        internalState.update { it.copy(isBusy = false) }
    }
}

fun String.toJapaneseDateTime(): String = runCatching {
    val value = OffsetDateTime.parse(this)
    "${value.year}年${value.monthValue}月${value.dayOfMonth}日 ${value.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}.getOrElse { this }
