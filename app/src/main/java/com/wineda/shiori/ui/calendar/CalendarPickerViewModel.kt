package com.wineda.shiori.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineda.shiori.data.repository.JournalRepository
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

data class CalendarDay(
    val date: LocalDate,
    val hasEntry: Boolean,
    val isPastEmpty: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

data class CalendarPickerUiState(
    val month: LocalDate,
    val days: List<CalendarDay> = emptyList(),
    val leadingBlanks: Int = 0,
    val selectedDate: LocalDate? = null,
)

@HiltViewModel
class CalendarPickerViewModel @Inject constructor(journalRepository: JournalRepository) : ViewModel() {
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val selectedMonth = MutableStateFlow(LocalDate(today.year, today.monthNumber, 1))
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    val uiState = combine(journalRepository.observeAll(), selectedMonth, selectedDate) { journals, month, selected ->
        val datesWithEntries = journals.map { it.date }.toSet()
        val monthDays = monthDays(month)
        CalendarPickerUiState(
            month = month,
            days = monthDays.map { date ->
                val hasEntry = datesWithEntries.contains(date)
                CalendarDay(
                    date = date,
                    hasEntry = hasEntry,
                    isPastEmpty = date < today && !hasEntry,
                    isToday = date == today,
                    isFuture = date > today,
                )
            },
            leadingBlanks = monthDays.firstOrNull()?.let { (it.dayOfWeek.value % 7) } ?: 0,
            selectedDate = selected?.takeIf {
                it.year == month.year &&
                    it.monthNumber == month.monthNumber &&
                    it < today &&
                    !datesWithEntries.contains(it)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarPickerUiState(selectedMonth.value))

    fun previousMonth() = selectedMonth.update { shiftMonth(it, -1) }.also { selectedDate.value = null }
    fun nextMonth() = selectedMonth.update { shiftMonth(it, 1) }.also { selectedDate.value = null }
    fun select(date: LocalDate) { selectedDate.value = date }

    private fun shiftMonth(date: LocalDate, amount: Int): LocalDate {
        val monthIndex = date.year * 12 + (date.monthNumber - 1) + amount
        return LocalDate(monthIndex / 12, monthIndex % 12 + 1, 1)
    }

    private fun monthDays(month: LocalDate): List<LocalDate> {
        val first = LocalDate(month.year, month.monthNumber, 1)
        val nextMonth = shiftMonth(first, 1)
        val last = nextMonth.minus(1, DateTimeUnit.DAY)
        return (1..last.dayOfMonth).map { LocalDate(month.year, month.monthNumber, it) }
    }
}
