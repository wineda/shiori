package com.wineda.shiori.domain.usecase

import com.wineda.shiori.data.backup.BackupData
import com.wineda.shiori.data.backup.BackupRepository
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(data: BackupData) = backupRepository.restoreBackup(data)
}
