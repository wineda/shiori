package com.wineda.shiori.domain.usecase

import com.wineda.shiori.data.repository.JournalRepository
import com.wineda.shiori.domain.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

class GetTodayJournalUseCase @Inject constructor(private val journalRepository: JournalRepository) {
    operator fun invoke(): Flow<Journal?> = journalRepository.observeByDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))
}
