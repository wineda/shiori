package com.wineda.shiori.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wineda.shiori.ui.archive.ArchiveDetailScreen
import com.wineda.shiori.ui.archive.ArchiveScreen
import com.wineda.shiori.ui.export.ExportScreen
import com.wineda.shiori.ui.home.HomeScreen
import com.wineda.shiori.ui.memo.MemoScreen
import com.wineda.shiori.ui.write.WriteScreen

@Composable
fun ShioriNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = ShioriDestination.Home.route) {
        composable(ShioriDestination.Home.route) {
            HomeScreen(
                onWrite = { navController.navigate(ShioriDestination.Write.route) },
                onMemo = { navController.navigate(ShioriDestination.Memo.route) },
            )
        }
        composable(ShioriDestination.Write.route) { WriteScreen(onBack = { navController.popBackStack() }) }
        composable(ShioriDestination.Memo.route) { MemoScreen() }
        composable(ShioriDestination.Archive.route) {
            ArchiveScreen(
                onExport = { navController.navigate(ShioriDestination.Export.route) },
                onDetail = { navController.navigate(ShioriDestination.ArchiveDetail.createRoute(it)) },
            )
        }
        composable(ShioriDestination.ArchiveDetail.route) {
            ArchiveDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(ShioriDestination.Write.route) },
            )
        }
        composable(ShioriDestination.Export.route) { ExportScreen() }
    }
}
