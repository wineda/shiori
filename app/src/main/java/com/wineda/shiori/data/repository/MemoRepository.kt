package com.wineda.shiori.data.repository

import com.wineda.shiori.data.local.dao.MemoDao
import com.wineda.shiori.data.local.entity.MemoEntity
import com.wineda.shiori.domain.model.Memo
import com.wineda.shiori.domain.model.MemoTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoRepository @Inject constructor(
    private val memoDao: MemoDao,
) {
    fun observeByDate(date: LocalDate): Flow<List<Memo>> =
        memoDao.observeByDate(date.toString()).map { list -> list.map { it.toDomain() } }

    fun observeCountByDate(date: LocalDate): Flow<Int> = memoDao.observeCountByDate(date.toString())

    suspend fun getInRange(from: LocalDate, to: LocalDate): List<Memo> =
        memoDao.getInRange(from.toString(), to.toString()).map { it.toDomain() }

    suspend fun save(date: LocalDate, text: String, tag: MemoTag?): Long = memoDao.insert(
        MemoEntity(
            date = date.toString(),
            timestamp = Clock.System.now().toEpochMilliseconds(),
            text = text,
            tag = tag?.key,
        ),
    )

    suspend fun delete(memo: Memo) = memoDao.delete(memo.toEntity())
}
