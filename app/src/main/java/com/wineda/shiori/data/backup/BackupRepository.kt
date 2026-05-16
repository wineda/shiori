package com.wineda.shiori.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.wineda.shiori.data.local.ShioriDatabase
import com.wineda.shiori.data.local.entity.JournalEntity
import com.wineda.shiori.data.local.entity.MemoEntity
import com.wineda.shiori.data.local.preferences.BackupPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ShioriDatabase,
    private val fileManager: BackupFileManager,
    private val preferences: BackupPreferences,
) {
    private val json = Json {
        prettyPrint = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }
    private val zone: ZoneId = ZoneId.systemDefault()

    val lastBackupAt: Flow<String?> = preferences.lastBackupAt

    fun requiresLegacyWritePermission(): Boolean = fileManager.requiresLegacyWritePermission()

    suspend fun createBackup(prefix: String = "shiori_backup", updateLastBackupAt: Boolean = true): BackupWriteResult {
        val now = ZonedDateTime.now(zone)
        val data = buildBackupData(now)
        val fileName = fileManager.writeBackupFile(fileBaseName(prefix, now), json.encodeToString(BackupData.serializer(), data))
        if (updateLastBackupAt) preferences.setLastBackupAt(data.exportedAt)
        return BackupWriteResult(fileName = fileName, exportedAt = data.exportedAt)
    }

    fun parseBackup(rawJson: String): BackupData = try {
        json.decodeFromString(BackupData.serializer(), rawJson).also { data ->
            if (data.schemaVersion != BACKUP_SCHEMA_VERSION) throw UnsupportedBackupException()
        }
    } catch (exception: UnsupportedBackupException) {
        throw exception
    } catch (exception: SerializationException) {
        throw BackupParseException(exception)
    } catch (exception: IllegalArgumentException) {
        throw BackupParseException(exception)
    }

    fun readAndParseBackup(uri: android.net.Uri): BackupData = parseBackup(fileManager.readText(uri))

    suspend fun restoreBackup(data: BackupData): RestoreResult {
        if (data.schemaVersion != BACKUP_SCHEMA_VERSION) throw UnsupportedBackupException()
        createBackup(prefix = "shiori_pre_restore", updateLastBackupAt = false)
        val journals = data.journals.map { it.toEntity() }
        val memos = data.memos.map { it.toEntity() }
        database.withTransaction {
            database.journalDao().deleteAll()
            database.memoDao().deleteAll()
            database.journalDao().insertAll(journals)
            database.memoDao().insertAll(memos)
        }
        preferences.setLastBackupAt(ZonedDateTime.now(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        return RestoreResult(journalCount = journals.size, memoCount = memos.size)
    }

    private suspend fun buildBackupData(now: ZonedDateTime): BackupData {
        val journals = database.journalDao().getAll().map { it.toBackupJournal() }
        val memos = database.memoDao().getAll().map { it.toBackupMemo() }
        return BackupData(
            appVersion = appVersion(),
            exportedAt = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            journals = journals,
            memos = memos,
        )
    }

    private fun JournalEntity.toBackupJournal(): BackupJournal = BackupJournal(
        date = date,
        good = good,
        hard = hard,
        insight = insight,
        tomorrow = tomorrow,
        createdAt = formatMillis(createdAt),
        updatedAt = formatMillis(updatedAt),
        isBackfilled = isBackfilled,
    )

    private fun MemoEntity.toBackupMemo(): BackupMemo = BackupMemo(
        id = id.toString(),
        date = date,
        time = formatMillis(timestamp).substringAfter('T').take(5),
        text = text,
        tag = tag,
        createdAt = formatMillis(timestamp),
    )

    private fun BackupJournal.toEntity(): JournalEntity = JournalEntity(
        date = date,
        good = good,
        hard = hard,
        insight = insight,
        tomorrow = tomorrow,
        createdAt = parseMillis(createdAt),
        updatedAt = parseMillis(updatedAt),
        isBackfilled = isBackfilled,
    )

    private fun BackupMemo.toEntity(): MemoEntity = MemoEntity(
        id = id.toLongOrNull() ?: 0L,
        date = date,
        timestamp = parseMillis(createdAt),
        text = text,
        tag = tag,
    )

    private fun formatMillis(value: Long): String = Instant.ofEpochMilli(value).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun parseMillis(value: String): Long = OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()

    private fun fileBaseName(prefix: String, time: ZonedDateTime): String = "${prefix}_${time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))}"

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")
}

data class BackupWriteResult(val fileName: String, val exportedAt: String)
data class RestoreResult(val journalCount: Int, val memoCount: Int)
class UnsupportedBackupException : Exception("このバージョンのバックアップには未対応です")
class BackupParseException(cause: Throwable) : Exception("ファイルを読み込めません。バックアップファイルが破損している可能性があります", cause)
