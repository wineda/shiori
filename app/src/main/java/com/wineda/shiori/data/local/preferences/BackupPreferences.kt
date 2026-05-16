package com.wineda.shiori.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore by preferencesDataStore(name = "backup_preferences")

@Singleton
class BackupPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    val lastBackupAt: Flow<String?> = context.backupDataStore.data.map { it[LAST_BACKUP_AT] }

    suspend fun setLastBackupAt(value: String) {
        context.backupDataStore.edit { preferences -> preferences[LAST_BACKUP_AT] = value }
    }

    private companion object {
        val LAST_BACKUP_AT = stringPreferencesKey("last_backup_at")
    }
}
