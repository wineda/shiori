package com.wineda.shiori.domain.usecase

import com.wineda.shiori.data.repository.MemoRepository
import com.wineda.shiori.domain.model.MemoTag
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class SaveMemoUseCase @Inject constructor(private val memoRepository: MemoRepository) {
    suspend operator fun invoke(date: LocalDate, text: String, tag: MemoTag?) = memoRepository.save(date, text, tag)
}
