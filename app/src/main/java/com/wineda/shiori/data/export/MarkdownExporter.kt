package com.wineda.shiori.data.export

import com.wineda.shiori.data.repository.JournalRepository
import com.wineda.shiori.data.repository.MemoRepository
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class MarkdownExporter @Inject constructor(
    private val journalRepository: JournalRepository,
    private val memoRepository: MemoRepository,
) {
    suspend fun export(from: LocalDate, to: LocalDate, includeMemos: Boolean, prompt: String?): String = buildString {
        prompt?.takeIf { it.isNotBlank() }?.let {
            appendLine("# 解析プロンプト")
            appendLine()
            appendLine(it)
            appendLine()
            appendLine("---")
            appendLine()
        }
        appendLine("# ジャーナル: $from - $to")
        appendLine()

        val journals = journalRepository.getInRange(from, to)
        val memosByDate = if (includeMemos) memoRepository.getInRange(from, to).groupBy { it.date } else emptyMap()
        if (journals.isEmpty()) {
            appendLine("_この期間のジャーナルはありません。_")
            return@buildString
        }
        journals.forEach { journal ->
            appendLine("## ${journal.date} (${dayOfWeekJa(journal.date)})")
            appendLine()
            appendSection("◯ 良かったこと", journal.good)
            appendSection("◐ しんどかったこと", journal.hard)
            appendSection("◑ 気付いたこと", journal.insight)
            appendSection("◉ 明日へのバトン", journal.tomorrow)
            memosByDate[journal.date]?.takeIf { it.isNotEmpty() }?.let { dayMemos ->
                appendLine("### 💭 メモ")
                dayMemos.forEach { memo ->
                    val tagLabel = memo.tag?.let { "[${it.label}] " } ?: ""
                    appendLine("- ${memo.timeString} $tagLabel${memo.text}")
                }
                appendLine()
            }
        }
    }

    private fun StringBuilder.appendSection(title: String, body: String) {
        if (body.isNotBlank()) {
            appendLine("### $title")
            appendLine(body)
            appendLine()
        }
    }

    private fun dayOfWeekJa(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "月"; 2 -> "火"; 3 -> "水"; 4 -> "木"; 5 -> "金"; 6 -> "土"; else -> "日"
    }
}
