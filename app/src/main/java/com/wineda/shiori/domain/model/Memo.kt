package com.wineda.shiori.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class Memo(
    val id: Long,
    val date: LocalDate,
    val timestamp: Instant,
    val text: String,
    val tag: MemoTag?,
) {
    val timeString: String
        get() = timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).time.let {
            "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
        }
}
