package com.wineda.shiori.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class BackupDataTest {
    @Test
    fun backupData_serializesSnakeCaseAndRoundTrips() {
        val data = BackupData(
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
                    text = "電車で読んでた本、刺さった。",
                    tag = "insight",
                    createdAt = "2026-05-15T08:14:00+09:00",
                ),
            ),
        )

        val encoded = Json.encodeToString(BackupData.serializer(), data)
        val decoded = Json.decodeFromString(BackupData.serializer(), encoded)

        assertTrue(encoded.contains("schema_version"))
        assertTrue(encoded.contains("created_at"))
        assertTrue(encoded.contains("is_backfilled"))
        assertEquals(1, decoded.schemaVersion)
        assertEquals("1.0.0", decoded.appVersion)
        assertEquals("insight", decoded.memos.single().tag)
        assertFalse(decoded.journals.single().isBackfilled)
    }
}
