package com.wineda.shiori.domain.usecase

import com.wineda.shiori.data.repository.JournalRepository
import com.wineda.shiori.domain.model.Journal
import javax.inject.Inject

class SaveJournalUseCase @Inject constructor(private val journalRepository: JournalRepository) {
    suspend operator fun invoke(journal: Journal) = journalRepository.save(journal)
}
