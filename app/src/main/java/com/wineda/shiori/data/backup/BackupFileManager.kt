package com.wineda.shiori.data.backup

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileManager @Inject constructor(@ApplicationContext private val context: Context) {
    fun writeBackup(prefix: String, timestampName: String, json: String): BackupWriteResult {
        val baseName = "${prefix}_${timestampName}.json"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(baseName, json)
        } else {
            writeWithFileApi(baseName, json)
        }
    }

    fun readText(uri: Uri): String = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: throw IOException("ファイルを開けません")

    private fun writeWithMediaStore(baseName: String, json: String): BackupWriteResult {
        val resolver = context.contentResolver
        val fileName = uniqueMediaStoreName(resolver, baseName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, BACKUP_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Files.getContentUri("external_primary")
        val uri = resolver.insert(collection, values) ?: throw IOException("保存先を作成できません")
        try {
            resolver.openOutputStream(uri)?.use { output -> output.write(json.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("保存先を開けません")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return BackupWriteResult(fileName, uri)
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            throw throwable
        }
    }

    private fun uniqueMediaStoreName(resolver: ContentResolver, baseName: String): String {
        val existing = mutableSetOf<String>()
        val collection = MediaStore.Files.getContentUri("external_primary")
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(BACKUP_RELATIVE_PATH),
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) existing += cursor.getString(nameIndex)
        }
        return uniqueName(baseName) { it in existing }
    }

    private fun writeWithFileApi(baseName: String, json: String): BackupWriteResult {
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val directory = File(documents, "Shiori")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("保存先フォルダを作成できません")
        val fileName = uniqueName(baseName) { File(directory, it).exists() }
        val file = File(directory, fileName)
        FileOutputStream(file).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return BackupWriteResult(fileName, Uri.fromFile(file))
    }

    private fun uniqueName(baseName: String, exists: (String) -> Boolean): String {
        if (!exists(baseName)) return baseName
        val stem = baseName.removeSuffix(".json")
        var suffix = 2
        while (true) {
            val candidate = "${stem}_${suffix}.json"
            if (!exists(candidate)) return candidate
            suffix++
        }
    }

    companion object {
        private const val BACKUP_RELATIVE_PATH = "Documents/Shiori/"
    }
}

data class BackupWriteResult(val fileName: String, val uri: Uri)
