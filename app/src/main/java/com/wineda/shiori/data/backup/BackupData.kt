package com.wineda.shiori.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    @SerialName("schema_version") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("app_version") val appVersion: String,
    @SerialName("exported_at") val exportedAt: String,
    val journals: List<BackupJournal>,
    val memos: List<BackupMemo>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupJournal(
    val date: String,
    val good: String,
    val hard: String,
    val insight: String,
    val tomorrow: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_backfilled") val isBackfilled: Boolean = false,
)

@Serializable
data class BackupMemo(
    val id: String,
    val date: String,
    val time: String,
    val text: String,
    val tag: String? = null,
    @SerialName("created_at") val createdAt: String,
)
