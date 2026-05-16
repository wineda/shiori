package com.wineda.shiori.util

import kotlinx.datetime.LocalDate

fun LocalDate.jaDate(): String = "${year}年${monthNumber}月${dayOfMonth}日 (${dayOfWeekJa()})"
fun LocalDate.monthDayJa(): String = "${monthNumber}月${dayOfMonth}日 (${dayOfWeekJa()})"
fun LocalDate.dayOfWeekJa(): String = when (dayOfWeek.value) {
    1 -> "月"; 2 -> "火"; 3 -> "水"; 4 -> "木"; 5 -> "金"; 6 -> "土"; else -> "日"
}
