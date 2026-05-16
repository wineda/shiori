package com.wineda.shiori.domain.usecase

import android.net.Uri
import com.wineda.shiori.data.backup.BackupRepository
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(private val backupRepository: BackupRepository) {
    fun read(uri: Uri) = backupRepository.readAndValidateBackup(uri)
    suspend fun restore(backup: com.wineda.shiori.data.backup.BackupData) = backupRepository.restoreBackup(backup)
}
