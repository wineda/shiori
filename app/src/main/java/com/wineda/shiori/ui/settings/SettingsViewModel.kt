package com.wineda.shiori.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineda.shiori.data.backup.BackupData
import com.wineda.shiori.data.backup.BackupParseException
import com.wineda.shiori.data.backup.BackupRepository
import com.wineda.shiori.data.backup.BackupStorageException
import com.wineda.shiori.data.backup.UnsupportedBackupException
import com.wineda.shiori.domain.usecase.CreateBackupUseCase
import com.wineda.shiori.domain.usecase.GetLastBackupInfoUseCase
import com.wineda.shiori.domain.usecase.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    getLastBackupInfo: GetLastBackupInfoUseCase,
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val backupRepository: BackupRepository,
) : ViewModel() {
    private val working = MutableStateFlow(false)
    private val pendingBackup = MutableStateFlow<BackupData?>(null)
    private var pendingPermissionAction: PendingPermissionAction? = null
    private val events = Channel<SettingsEvent>(Channel.BUFFERED)

    val uiState: StateFlow<SettingsUiState> = combine(
        getLastBackupInfo(),
        working,
        pendingBackup,
    ) { lastBackupAt, isWorking, pending ->
        SettingsUiState(
            isWorking = isWorking,
            lastBackupText = lastBackupAt?.toJapaneseDateTime() ?: "未実施",
            pendingRestore = pending?.let {
                PendingRestoreUiState(
                    journalCount = it.journals.size,
                    memoCount = it.memos.size,
                    exportedAtText = it.exportedAt.toJapaneseDateTime(),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    val uiEvents = events.receiveAsFlow()

    fun createBackupFromUserAction() {
        runWithLegacyPermissionIfNeeded(PendingPermissionAction.CreateBackup) { createBackupInternal() }
    }

    fun restoreFromUserAction() {
        runWithLegacyPermissionIfNeeded(PendingPermissionAction.OpenRestorePicker) {
            sendEvent(SettingsEvent.OpenBackupPicker)
        }
    }

    fun onWritePermissionResult(granted: Boolean) {
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (!granted) {
            sendEvent(SettingsEvent.ShowMessage("書き込み権限が拒否されました"))
            return
        }
        when (action) {
            PendingPermissionAction.CreateBackup -> createBackupInternal()
            PendingPermissionAction.OpenRestorePicker -> sendEvent(SettingsEvent.OpenBackupPicker)
            null -> Unit
        }
    }

    fun loadBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                working.update { true }
                withContext(Dispatchers.IO) { backupRepository.readAndParseBackup(uri) }
            }.onSuccess { data ->
                pendingBackup.value = data
            }.onFailure { throwable ->
                sendEvent(SettingsEvent.ShowMessage(throwable.toUserMessageForRead()))
            }
            working.update { false }
        }
    }

    fun dismissRestoreDialog() {
        pendingBackup.value = null
    }

    fun confirmRestore() {
        val backup = pendingBackup.value ?: return
        viewModelScope.launch {
            runCatching {
                working.update { true }
                withContext(Dispatchers.IO) { restoreBackup(backup) }
            }.onSuccess { result ->
                pendingBackup.value = null
                sendEvent(SettingsEvent.ShowMessage("復元しました（ジャーナル ${result.journalCount} 件・メモ ${result.memoCount} 件）"))
            }.onFailure { throwable ->
                sendEvent(SettingsEvent.ShowMessage(throwable.toUserMessageForRestore()))
            }
            working.update { false }
        }
    }

    private fun runWithLegacyPermissionIfNeeded(action: PendingPermissionAction, block: () -> Unit) {
        if (backupRepository.requiresLegacyWritePermission()) {
            pendingPermissionAction = action
            sendEvent(SettingsEvent.RequestWritePermission)
            return
        }
        block()
    }

    private fun createBackupInternal() {
        viewModelScope.launch {
            runCatching {
                working.update { true }
                withContext(Dispatchers.IO) { createBackup() }
            }.onSuccess { result ->
                sendEvent(SettingsEvent.ShowMessage("バックアップを作成しました\n${result.fileName}"))
            }.onFailure { throwable ->
                sendEvent(SettingsEvent.ShowMessage("バックアップに失敗しました: ${throwable.toUserMessage()}"))
            }
            working.update { false }
        }
    }

    private fun sendEvent(event: SettingsEvent) {
        viewModelScope.launch { events.send(event) }
    }
}

data class SettingsUiState(
    val isWorking: Boolean = false,
    val lastBackupText: String = "未実施",
    val pendingRestore: PendingRestoreUiState? = null,
)

data class PendingRestoreUiState(
    val journalCount: Int,
    val memoCount: Int,
    val exportedAtText: String,
)

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
    data object RequestWritePermission : SettingsEvent
    data object OpenBackupPicker : SettingsEvent
}

private enum class PendingPermissionAction { CreateBackup, OpenRestorePicker }

private fun Throwable.toUserMessageForRead(): String = when (this) {
    is UnsupportedBackupException -> "このバージョンのバックアップには未対応です"
    is BackupParseException -> "ファイルを読み込めません。バックアップファイルが破損している可能性があります"
    else -> "ファイルが読めません"
}

private fun Throwable.toUserMessageForRestore(): String = when (this) {
    is BackupStorageException -> "現データの退避に失敗したため復元を中止しました"
    is UnsupportedBackupException -> "このバージョンのバックアップには未対応です"
    else -> "復元に失敗しました: ${toUserMessage()}"
}

private fun Throwable.toUserMessage(): String = message ?: "不明なエラー"

private fun String.toJapaneseDateTime(): String = runCatching {
    OffsetDateTime.parse(this).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.JAPAN))
}.getOrDefault(this)
