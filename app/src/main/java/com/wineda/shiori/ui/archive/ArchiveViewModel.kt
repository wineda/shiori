package com.wineda.shiori.ui.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineda.shiori.data.repository.JournalRepository
import com.wineda.shiori.data.repository.MemoRepository
import com.wineda.shiori.domain.model.Journal
import com.wineda.shiori.domain.model.Memo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class ArchiveUiState(val month: LocalDate, val journals: List<Journal> = emptyList(), val monthlyCount: Int = 0, val currentStreak: Int = 0)
data class ArchiveDetailUiState(val journal: Journal? = null, val memos: List<Memo> = emptyList())

@HiltViewModel
class ArchiveViewModel @Inject constructor(journalRepository: JournalRepository) : ViewModel() {
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val selectedMonth = MutableStateFlow(LocalDate(today.year, today.monthNumber, 1))
    val uiState = combine(journalRepository.observeAll(), selectedMonth) { all, month ->
        val monthly = all.filter { it.date.year == month.year && it.date.monthNumber == month.monthNumber && !it.isEmpty }
        ArchiveUiState(month, monthly, monthly.size, calculateStreak(all, today))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState(selectedMonth.value))

    fun previousMonth() = selectedMonth.update { shiftMonth(it, -1) }
    fun nextMonth() = selectedMonth.update { shiftMonth(it, 1) }

    private fun shiftMonth(date: LocalDate, amount: Int): LocalDate {
        val monthIndex = date.year * 12 + (date.monthNumber - 1) + amount
        return LocalDate(monthIndex / 12, monthIndex % 12 + 1, 1)
    }

    private fun calculateStreak(all: List<Journal>, today: LocalDate): Int {
        val dates = all.filterNot { it.isEmpty }.map { it.date }.toSet()
        var streak = 0
        var cursor = today
        while (dates.contains(cursor)) {
            streak++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }
}

@HiltViewModel
class ArchiveDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    journalRepository: JournalRepository,
    memoRepository: MemoRepository,
) : ViewModel() {
    private val date = LocalDate.parse(checkNotNull(savedStateHandle.get<String>("date")))
    val uiState = combine(journalRepository.observeByDate(date), memoRepository.observeByDate(date)) { journal, memos ->
        ArchiveDetailUiState(journal, memos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveDetailUiState())
}
