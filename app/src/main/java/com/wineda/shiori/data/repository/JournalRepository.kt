package com.wineda.shiori.data.repository

import com.wineda.shiori.data.local.dao.JournalDao
import com.wineda.shiori.domain.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val journalDao: JournalDao,
) {
    fun observeByDate(date: LocalDate): Flow<Journal?> =
        journalDao.observeByDate(date.toString()).map { it?.toDomain() }

    fun observeAll(): Flow<List<Journal>> = journalDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeEntryCount(): Flow<Int> = journalDao.observeEntryCount()

    suspend fun getByDate(date: LocalDate): Journal? = journalDao.getByDate(date.toString())?.toDomain()

    suspend fun getInRange(from: LocalDate, to: LocalDate): List<Journal> =
        journalDao.getInRange(from.toString(), to.toString()).map { it.toDomain() }

    suspend fun getTomorrowBaton(date: LocalDate): String? = journalDao.getTomorrowBaton(date.toString())

    suspend fun save(journal: Journal) {
        val existing = journalDao.getByDate(journal.date.toString())
        val now = Clock.System.now()
        journalDao.upsert(journal.copy(updatedAt = now).toEntity(existing?.createdAt))
    }
}
