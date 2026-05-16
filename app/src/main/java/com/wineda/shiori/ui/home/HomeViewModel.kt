package com.wineda.shiori.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineda.shiori.data.repository.JournalRepository
import com.wineda.shiori.data.repository.MemoRepository
import com.wineda.shiori.domain.model.Journal
import com.wineda.shiori.domain.usecase.GetYesterdayBatonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class HomeUiState(
    val today: LocalDate,
    val memoCount: Int = 0,
    val baton: String? = null,
    val streak: List<Boolean> = List(7) { false },
    val unfilledThisMonthCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    memoRepository: MemoRepository,
    getYesterdayBaton: GetYesterdayBatonUseCase,
) : ViewModel() {
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val baton = MutableStateFlow<String?>(null)
    private val streak = MutableStateFlow(List(7) { false })

    val uiState = combine(memoRepository.observeCountByDate(today), baton, streak, journalRepository.observeAll()) { memoCount, batonValue, streakValue, journals ->
        HomeUiState(
            today = today,
            memoCount = memoCount,
            baton = batonValue,
            streak = streakValue,
            unfilledThisMonthCount = countRecentUnfilledDays(journals),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today))

    init {
        viewModelScope.launch { baton.value = getYesterdayBaton(today) }
        viewModelScope.launch {
            val days = (0..6).map { offset -> today.minus(offset, DateTimeUnit.DAY) }
            val written = days.map { date -> journalRepository.getByDate(date)?.isEmpty == false }
            streak.value = written.reversed()
        }
    }

    private fun countRecentUnfilledDays(journals: List<Journal>): Int {
        val datesWithEntries = journals.map { it.date }.toSet()
        return (1..30)
            .map { offset -> today.minus(offset, DateTimeUnit.DAY) }
            .count { date ->
                date.year == today.year && date.monthNumber == today.monthNumber && !datesWithEntries.contains(date)
            }
    }
}
