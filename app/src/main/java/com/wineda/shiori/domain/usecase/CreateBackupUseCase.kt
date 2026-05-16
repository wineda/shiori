package com.wineda.shiori.domain.usecase

import com.wineda.shiori.data.backup.BackupRepository
import javax.inject.Inject

class CreateBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke() = backupRepository.createBackup()
}
