package com.wineda.shiori.data.backup

import androidx.room.withTransaction
import com.wineda.shiori.BuildConfig
import com.wineda.shiori.data.local.ShioriDatabase
import com.wineda.shiori.data.local.entity.JournalEntity
import com.wineda.shiori.data.local.entity.MemoEntity
import com.wineda.shiori.data.local.preferences.BackupPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Singleton
class BackupRepository @Inject constructor(
    private val database: ShioriDatabase,
    private val fileManager: BackupFileManager,
    private val preferences: BackupPreferences,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    val lastBackupAt: Flow<String?> = preferences.lastBackupAt

    suspend fun createBackup(prefix: String = BACKUP_PREFIX): BackupWriteResult {
        val now = OffsetDateTime.now()
        val data = buildBackupData(now)
        val fileJson = json.encodeToString(BackupData.serializer(), data)
        val result = fileManager.writeBackup(prefix, now.format(FILE_TIMESTAMP_FORMATTER), fileJson)
        preferences.setLastBackupAt(data.exportedAt)
        return result
    }

    fun readAndValidateBackup(uri: android.net.Uri): BackupData {
        val text = try {
            fileManager.readText(uri)
        } catch (throwable: Throwable) {
            throw BackupReadException("ファイルを読み込めません。バックアップファイルが破損している可能性があります", throwable)
        }
        val backup = try {
            json.decodeFromString(BackupData.serializer(), text)
        } catch (throwable: SerializationException) {
            throw BackupReadException("ファイルを読み込めません。バックアップファイルが破損している可能性があります", throwable)
        } catch (throwable: IllegalArgumentException) {
            throw BackupReadException("ファイルを読み込めません。バックアップファイルが破損している可能性があります", throwable)
        }
        if (backup.schemaVersion != BackupData.SCHEMA_VERSION) {
            throw UnsupportedBackupException("このバージョンのバックアップには未対応です")
        }
        validateBackupFields(backup)
        return backup
    }

    suspend fun restoreBackup(backup: BackupData): RestoreResult {
        try {
            createBackup(PRE_RESTORE_PREFIX)
        } catch (throwable: Throwable) {
            throw BackupWriteException("現データの退避に失敗したため復元を中止しました", throwable)
        }

        val journals = backup.journals.map { it.toEntity() }
        val memos = backup.memos.map { it.toEntity() }
        database.withTransaction {
            database.memoDao().deleteAll()
            database.journalDao().deleteAll()
            database.journalDao().insertAll(journals)
            database.memoDao().insertAll(memos)
        }
        preferences.setLastBackupAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        return RestoreResult(journals.size, memos.size)
    }

    private fun validateBackupFields(backup: BackupData) {
        try {
            OffsetDateTime.parse(backup.exportedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            backup.journals.forEach { journal ->
                LocalDate.parse(journal.date)
                journal.createdAt.toEpochMilliseconds()
                journal.updatedAt.toEpochMilliseconds()
            }
            backup.memos.forEach { memo ->
                LocalDate.parse(memo.date)
                LocalTime.parse(memo.time)
                memo.createdAt.toEpochMilliseconds()
            }
        } catch (throwable: Throwable) {
            throw BackupReadException("ファイルを読み込めません。バックアップファイルが破損している可能性があります", throwable)
        }
    }

    private suspend fun buildBackupData(exportedAt: OffsetDateTime): BackupData = BackupData(
        appVersion = BuildConfig.VERSION_NAME,
        exportedAt = exportedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        journals = database.journalDao().getAll().map { it.toBackupJournal() },
        memos = database.memoDao().getAll().map { it.toBackupMemo() },
    )

    private fun JournalEntity.toBackupJournal(): BackupJournal = BackupJournal(
        date = date,
        good = good,
        hard = hard,
        insight = insight,
        tomorrow = tomorrow,
        createdAt = createdAt.toOffsetDateTimeString(),
        updatedAt = updatedAt.toOffsetDateTimeString(),
        isBackfilled = false,
    )

    private fun MemoEntity.toBackupMemo(): BackupMemo {
        val createdAt = timestamp.toOffsetDateTime()
        return BackupMemo(
            id = id.toString(),
            date = date,
            time = createdAt.format(DateTimeFormatter.ofPattern("HH:mm")),
            text = text,
            tag = tag,
            createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    private fun BackupJournal.toEntity(): JournalEntity = JournalEntity(
        date = date,
        good = good,
        hard = hard,
        insight = insight,
        tomorrow = tomorrow,
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt.toEpochMilliseconds(),
    )

    private fun BackupMemo.toEntity(): MemoEntity = MemoEntity(
        id = id.toLongOrNull() ?: 0,
        date = date,
        timestamp = createdAt.toEpochMilliseconds(),
        text = text,
        tag = tag?.takeIf { candidate -> candidate in SUPPORTED_TAGS },
    )

    private fun Long.toOffsetDateTime(): OffsetDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toOffsetDateTime()
    private fun Long.toOffsetDateTimeString(): String = toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    private fun String.toEpochMilliseconds(): Long = OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()

    companion object {
        private const val BACKUP_PREFIX = "shiori_backup"
        private const val PRE_RESTORE_PREFIX = "shiori_pre_restore"
        private val FILE_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        private val SUPPORTED_TAGS = setOf("good", "hard", "insight", "tomorrow")
    }
}

data class RestoreResult(val journalCount: Int, val memoCount: Int)
class BackupReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
class UnsupportedBackupException(message: String) : Exception(message)
class BackupWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)
