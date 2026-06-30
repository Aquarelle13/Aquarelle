package com.psp.shifthelper

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.psp.shifthelper.navigation.NavGraph
import com.psp.shifthelper.navigation.Screen
import com.psp.shifthelper.ui.theme.PSPShiftHelperTheme

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 화면 캡처 및 녹화 방지 (보안 설정)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            PSPShiftHelperTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val items = listOf(
                    BottomNavItem("HOME", Icons.Filled.Home, Screen.Home),
                    BottomNavItem("AUTO", Icons.Filled.List, Screen.Auto),
                    BottomNavItem("HISTORY", Icons.Filled.History, Screen.History),
                    BottomNavItem("MANAGE", Icons.Filled.Settings, Screen.Manage)
                )

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            items.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.screen.route,
                                    onClick = {
                                        navController.navigate(item.screen.route) {
                                            // 탭 이동 시 상태 보존을 위한 표준 설정
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavGraph(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}