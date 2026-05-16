package com.wineda.shiori.domain.usecase

import com.wineda.shiori.data.export.MarkdownExporter
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class ExportMarkdownUseCase @Inject constructor(private val markdownExporter: MarkdownExporter) {
    suspend operator fun invoke(from: LocalDate, to: LocalDate, includeMemos: Boolean, prompt: String?) =
        markdownExporter.export(from, to, includeMemos, prompt)
}
