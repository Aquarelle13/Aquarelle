package com.psp.shifthelper.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.psp.shifthelper.ui.auto.AutoAssignScreen
import com.psp.shifthelper.ui.history.HistoryScreen
import com.psp.shifthelper.ui.home.HomeScreen
import com.psp.shifthelper.ui.manage.ManageScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Auto : Screen("auto")
    object History : Screen("history")
    object Manage : Screen("manage")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) { 
            HomeScreen(
                onNavigateToOcr = {
                    navController.navigate(Screen.Auto.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            ) 
        }
        composable(Screen.Auto.route) { AutoAssignScreen() }
        composable(Screen.History.route) { HistoryScreen() }
        composable(Screen.Manage.route) { ManageScreen() }
    }
}