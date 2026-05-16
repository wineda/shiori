package com.wineda.shiori.navigation

sealed class ShioriDestination(val route: String) {
    data object Home : ShioriDestination("home")
    data object Write : ShioriDestination("write")
    data object Memo : ShioriDestination("memo")
    data object Archive : ShioriDestination("archive")
    data object ArchiveDetail : ShioriDestination("archive/{date}") {
        fun createRoute(date: String) = "archive/$date"
    }
    data object Export : ShioriDestination("export")
}
