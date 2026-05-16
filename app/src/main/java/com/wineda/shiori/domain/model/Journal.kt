package com.wineda.shiori.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class Journal(
    val date: LocalDate,
    val good: String,
    val hard: String,
    val insight: String,
    val tomorrow: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isEmpty: Boolean
        get() = good.isBlank() && hard.isBlank() && insight.isBlank() && tomorrow.isBlank()
}
