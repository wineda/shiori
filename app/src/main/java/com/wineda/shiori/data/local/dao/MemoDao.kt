package com.wineda.shiori.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.wineda.shiori.data.local.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE date = :date ORDER BY timestamp ASC")
    fun observeByDate(date: String): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE date >= :from AND date <= :to ORDER BY timestamp ASC")
    suspend fun getInRange(from: String, to: String): List<MemoEntity>

    @Query("SELECT COUNT(*) FROM memos WHERE date = :date")
    fun observeCountByDate(date: String): Flow<Int>

    @Insert
    suspend fun insert(memo: MemoEntity): Long

    @Delete
    suspend fun delete(memo: MemoEntity)
}
