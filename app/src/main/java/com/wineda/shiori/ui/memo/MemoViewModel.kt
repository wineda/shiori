package com.wineda.shiori.ui.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineda.shiori.data.repository.MemoRepository
import com.wineda.shiori.domain.model.Memo
import com.wineda.shiori.domain.model.MemoTag
import com.wineda.shiori.domain.usecase.SaveMemoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class MemoUiState(val today: LocalDate, val memos: List<Memo> = emptyList(), val input: String = "", val selectedTag: MemoTag? = null)

@HiltViewModel
class MemoViewModel @Inject constructor(
    memoRepository: MemoRepository,
    private val saveMemo: SaveMemoUseCase,
) : ViewModel() {
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val inputState = MutableStateFlow(Pair<String, MemoTag?>("", null))
    val uiState = combine(memoRepository.observeByDate(today), inputState) { memos, (input, tag) ->
        MemoUiState(today, memos, input, tag)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoUiState(today))

    fun onInputChanged(value: String) = inputState.update { value to it.second }
    fun onTagSelected(tag: MemoTag?) = inputState.update { it.first to if (it.second == tag) null else tag }
    fun send() = viewModelScope.launch {
        val (text, tag) = inputState.value
        if (text.isNotBlank()) {
            saveMemo(today, text.trim(), tag)
            inputState.value = "" to null
        }
    }
}
