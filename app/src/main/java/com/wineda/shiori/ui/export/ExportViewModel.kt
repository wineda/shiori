package com.wineda.shiori.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineda.shiori.data.export.AnalysisPrompts
import com.wineda.shiori.domain.usecase.ExportMarkdownUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import javax.inject.Inject

enum class ExportRange(val label: String, val days: Int) {
    WEEK("1週間", 7),
    TWO_WEEKS("2週間", 14),
    MONTH("1ヶ月", 30),
    CUSTOM("指定", 14),
}
enum class PromptPreset(val label: String, val prompt: String) {
    PATTERNS("感情パターン", AnalysisPrompts.patterns),
    HIDDEN("見落とし発見", AnalysisPrompts.hiddenInsights),
    BATON("バトン達成", AnalysisPrompts.batonAchievement),
    CUSTOM("カスタム", AnalysisPrompts.custom),
}

data class ExportUiState(
    val range: ExportRange = ExportRange.WEEK,
    val promptPreset: PromptPreset = PromptPreset.PATTERNS,
    val customPrompt: String = "",
    val includeMemos: Boolean = true,
    val includePrompt: Boolean = true,
    val markdown: String = "",
    val isGenerating: Boolean = false,
)

@HiltViewModel
class ExportViewModel @Inject constructor(private val exportMarkdown: ExportMarkdownUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState = _uiState.asStateFlow()

    fun selectRange(range: ExportRange) = _uiState.update { it.copy(range = range) }
    fun selectPrompt(preset: PromptPreset) = _uiState.update { it.copy(promptPreset = preset) }
    fun updateCustomPrompt(value: String) = _uiState.update { it.copy(customPrompt = value) }
    fun toggleMemos() = _uiState.update { it.copy(includeMemos = !it.includeMemos) }
    fun togglePrompt() = _uiState.update { it.copy(includePrompt = !it.includePrompt) }

    fun generate() = viewModelScope.launch {
        _uiState.update { it.copy(isGenerating = true) }
        val state = _uiState.value
        val to = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val from = to.minus(state.range.days - 1, DateTimeUnit.DAY)
        val prompt = if (!state.includePrompt) null else if (state.promptPreset == PromptPreset.CUSTOM) state.customPrompt else state.promptPreset.prompt
        val markdown = exportMarkdown(from, to, state.includeMemos, prompt)
        _uiState.update { it.copy(markdown = markdown, isGenerating = false) }
    }
}
