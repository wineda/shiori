package com.wineda.shiori

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wineda.shiori.navigation.ShioriDestination
import com.wineda.shiori.navigation.ShioriNavHost
import com.wineda.shiori.ui.components.ShioriTabBar
import com.wineda.shiori.ui.theme.ShioriColors
import com.wineda.shiori.ui.theme.ShioriTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShioriTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showBottomBar = currentRoute in setOf(
                    ShioriDestination.Home.route,
                    ShioriDestination.Memo.route,
                    ShioriDestination.Archive.route,
                    ShioriDestination.ArchiveDetail.route,
                    ShioriDestination.Export.route,
                )
                Scaffold(
                    modifier = Modifier.fillMaxSize().background(ShioriColors.Paper),
                    containerColor = ShioriColors.Paper,
                    bottomBar = {
                        if (showBottomBar) {
                            ShioriTabBar(currentRoute = currentRoute) { route ->
                                navController.navigate(route) {
                                    popUpTo(ShioriDestination.Home.route)
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(innerPadding).fillMaxSize()) {
                        ShioriNavHost(navController = navController)
                    }
                }
            }
        }
    }
}
