package com.wineda.shiori.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class BackupDataTest {
    private val json = Json { prettyPrint = true; explicitNulls = true }

    @Test
    fun serializesSnakeCaseFields() {
        val backup = BackupData(
            appVersion = "1.0.0",
            exportedAt = "2026-05-17T09:30:00+09:00",
            journals = listOf(
                BackupJournal(
                    date = "2026-05-15",
                    good = "good",
                    hard = "hard",
                    insight = "insight",
                    tomorrow = "tomorrow",
                    createdAt = "2026-05-15T22:30:00+09:00",
                    updatedAt = "2026-05-15T22:35:00+09:00",
                    isBackfilled = false,
                ),
            ),
            memos = listOf(
                BackupMemo(
                    id = "1",
                    date = "2026-05-15",
                    time = "08:14",
                    text = "memo",
                    tag = null,
                    createdAt = "2026-05-15T08:14:00+09:00",
                ),
            ),
        )

        val encoded = json.encodeToString(BackupData.serializer(), backup)

        assertTrue(encoded.contains("schema_version"))
        assertTrue(encoded.contains("app_version"))
        assertTrue(encoded.contains("created_at"))
        assertTrue(encoded.contains("is_backfilled"))
    }

    @Test
    fun deserializesBackupJson() {
        val decoded = json.decodeFromString(
            BackupData.serializer(),
            """
            {
              "schema_version": 1,
              "app_version": "1.0.0",
              "exported_at": "2026-05-17T09:30:00+09:00",
              "journals": [
                {
                  "date": "2026-05-15",
                  "good": "...",
                  "hard": "...",
                  "insight": "...",
                  "tomorrow": "...",
                  "created_at": "2026-05-15T22:30:00+09:00",
                  "updated_at": "2026-05-15T22:35:00+09:00",
                  "is_backfilled": false
                }
              ],
              "memos": [
                {
                  "id": "uuid-or-timestamp",
                  "date": "2026-05-15",
                  "time": "08:14",
                  "text": "電車で読んでた本、刺さった。",
                  "tag": null,
                  "created_at": "2026-05-15T08:14:00+09:00"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, decoded.schemaVersion)
        assertEquals("2026-05-15", decoded.journals.single().date)
        assertFalse(decoded.journals.single().isBackfilled)
        assertNull(decoded.memos.single().tag)
    }
}
