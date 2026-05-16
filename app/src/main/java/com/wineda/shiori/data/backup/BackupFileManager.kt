package com.wineda.shiori.data.backup

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun requiresLegacyWritePermission(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    fun writeBackupFile(baseNameWithoutExtension: String, json: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeWithMediaStore(baseNameWithoutExtension, json)
        }
        if (requiresLegacyWritePermission()) {
            throw BackupStorageException("書き込み権限がありません")
        }
        return writeWithLegacyFileApi(baseNameWithoutExtension, json)
    }

    fun readText(uri: Uri): String = context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
    } ?: throw BackupStorageException("ファイルを開けません")

    private fun writeWithMediaStore(baseNameWithoutExtension: String, json: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external_primary")
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/Shiori/"
        val fileName = nextMediaStoreName(baseNameWithoutExtension, relativePath)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw BackupStorageException("保存先を作成できません")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw BackupStorageException("ファイルへ書き込めません")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return fileName
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            if (throwable is IOException) throw BackupStorageException("容量不足またはストレージに書き込めません", throwable)
            throw throwable
        }
    }

    private fun nextMediaStoreName(baseNameWithoutExtension: String, relativePath: String): String {
        var index = 1
        while (true) {
            val candidate = candidateName(baseNameWithoutExtension, index)
            val exists = context.contentResolver.query(
                MediaStore.Files.getContentUri("external_primary"),
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(candidate, relativePath),
                null,
            )?.use { it.moveToFirst() } ?: false
            if (!exists) return candidate
            index++
        }
    }

    private fun writeWithLegacyFileApi(baseNameWithoutExtension: String, json: String): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Shiori")
        if (!dir.exists() && !dir.mkdirs()) throw BackupStorageException("保存先フォルダを作成できません")
        var index = 1
        while (true) {
            val fileName = candidateName(baseNameWithoutExtension, index)
            val file = File(dir, fileName)
            if (!file.exists()) {
                try {
                    file.writeText(json, Charsets.UTF_8)
                } catch (exception: IOException) {
                    throw BackupStorageException("容量不足またはストレージに書き込めません", exception)
                }
                return fileName
            }
            index++
        }
    }

    private fun candidateName(baseNameWithoutExtension: String, index: Int): String =
        if (index == 1) "$baseNameWithoutExtension.json" else "${baseNameWithoutExtension}_$index.json"
}

class BackupStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
