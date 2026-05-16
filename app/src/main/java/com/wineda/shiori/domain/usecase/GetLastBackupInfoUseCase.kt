package com.wineda.shiori.domain.usecase

import com.wineda.shiori.data.backup.BackupRepository
import javax.inject.Inject

class GetLastBackupInfoUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    operator fun invoke() = backupRepository.lastBackupAt
}
