package com.wineda.shiori.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wineda.shiori.data.local.dao.JournalDao
import com.wineda.shiori.data.local.dao.MemoDao
import com.wineda.shiori.data.local.entity.JournalEntity
import com.wineda.shiori.data.local.entity.MemoEntity

@Database(entities = [JournalEntity::class, MemoEntity::class], version = 2, exportSchema = true)
abstract class ShioriDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    abstract fun memoDao(): MemoDao
}
