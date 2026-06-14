package com.example.tasama.presentation.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.tasama.navigation.BottomNavItem
import com.example.tasama.presentation.ai.AIScreen
import com.example.tasama.presentation.chat.ChatScreen
import com.example.tasama.presentation.dashboard.DashboardScreen
import com.example.tasama.presentation.profile.ProfileScreen
import com.example.tasama.presentation.savings.SavingsScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Savings,
        BottomNavItem.AIAdvisor,
        BottomNavItem.Chat,
        BottomNavItem.Profile
    )

    MainContent(
        navController = navController,
        items = items,
        content = { padding ->
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Dashboard.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(BottomNavItem.Dashboard.route) {
                    DashboardScreen(
                        viewModel = koinViewModel(),
                        onTransactionClick = { /* Handle navigation to list if needed */ }
                    )
                }
                composable(BottomNavItem.Savings.route) {
                    SavingsScreen()
                }
                composable(BottomNavItem.AIAdvisor.route) {
                    AIScreen()
                }
                composable(BottomNavItem.Chat.route) {
                    ChatScreen()
                }
                composable(BottomNavItem.Profile.route) {
                    ProfileScreen()
                }
            }
        }
    )
}

@Composable
fun MainContent(
    navController: NavController,
    items: List<BottomNavItem>,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Text(item.emoji)
                        },
                        label = {
                            Text(item.title)
                        }
                    )
                }
            }
        }
    ) { padding ->
        content(padding)
    }
}

@Preview
@Composable
fun MainPreview() {
    val navController = rememberNavController()
    MaterialTheme {
        MainContent(
            navController = navController,
            items = listOf(
                BottomNavItem.Dashboard,
                BottomNavItem.Savings,
                BottomNavItem.AIAdvisor,
                BottomNavItem.Chat,
                BottomNavItem.Profile
            ),
            content = {
                Text("Content Area")
            }
        )
    }
}
