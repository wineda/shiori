package com.wineda.shiori.ui.write

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineda.shiori.data.repository.JournalRepository
import com.wineda.shiori.domain.model.Journal
import com.wineda.shiori.domain.usecase.GetYesterdayBatonUseCase
import com.wineda.shiori.domain.usecase.SaveJournalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class WriteUiState(val journal: Journal, val baton: String? = null, val saved: Boolean = true)

@OptIn(FlowPreview::class)
@HiltViewModel
class WriteViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val saveJournal: SaveJournalUseCase,
    private val getYesterdayBaton: GetYesterdayBatonUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val targetDate: LocalDate = savedStateHandle.get<String>("date")
        ?.let { LocalDate.parse(it) }
        ?: today
    private val emptyJournal = Journal(targetDate, "", "", "", "", Clock.System.now(), Clock.System.now())
    private val _uiState = MutableStateFlow(WriteUiState(emptyJournal))
    val uiState: StateFlow<WriteUiState> = _uiState.asStateFlow()
    private val saveDebouncer = MutableSharedFlow<Journal>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(journal = journalRepository.getByDate(targetDate) ?: emptyJournal, baton = getYesterdayBaton(targetDate)) }
        }
        saveDebouncer.debounce(1_500.milliseconds).onEach { journal ->
            saveJournal(journal)
            _uiState.update { it.copy(saved = true) }
        }.launchIn(viewModelScope)
    }

    fun onFieldChanged(good: String? = null, hard: String? = null, insight: String? = null, tomorrow: String? = null) {
        val updated = _uiState.value.journal.let {
            it.copy(
                good = good ?: it.good,
                hard = hard ?: it.hard,
                insight = insight ?: it.insight,
                tomorrow = tomorrow ?: it.tomorrow,
                updatedAt = Clock.System.now(),
            )
        }
        _uiState.update { it.copy(journal = updated, saved = false) }
        saveDebouncer.tryEmit(updated)
    }

    fun saveNow() = viewModelScope.launch {
        saveJournal(_uiState.value.journal)
        _uiState.update { it.copy(saved = true) }
    }
}
