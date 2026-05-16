package com.wineda.shiori.domain.usecase

import com.wineda.shiori.data.repository.JournalRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import javax.inject.Inject

class GetYesterdayBatonUseCase @Inject constructor(private val journalRepository: JournalRepository) {
    suspend operator fun invoke(today: LocalDate): String? {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        return journalRepository.getTomorrowBaton(yesterday)?.takeIf { it.isNotBlank() }
    }
}
