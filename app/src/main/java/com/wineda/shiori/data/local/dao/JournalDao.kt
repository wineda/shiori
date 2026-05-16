package com.wineda.shiori.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wineda.shiori.data.local.entity.JournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM journals WHERE date = :date")
    suspend fun getByDate(date: String): JournalEntity?

    @Query("SELECT * FROM journals WHERE date = :date")
    fun observeByDate(date: String): Flow<JournalEntity?>

    @Query("SELECT * FROM journals ORDER BY date DESC")
    fun observeAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journals ORDER BY date ASC")
    suspend fun getAll(): List<JournalEntity>

    @Query("SELECT * FROM journals WHERE date >= :from AND date <= :to ORDER BY date ASC")
    suspend fun getInRange(from: String, to: String): List<JournalEntity>

    @Query("SELECT tomorrow FROM journals WHERE date = :date AND isBackfilled = 0")
    suspend fun getTomorrowBaton(date: String): String?

    @Query("SELECT COUNT(*) FROM journals WHERE good != '' OR hard != '' OR insight != '' OR tomorrow != ''")
    fun observeEntryCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(journal: JournalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(journals: List<JournalEntity>)

    @Query("DELETE FROM journals")
    suspend fun deleteAll()
}
