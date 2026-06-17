package com.example.tasama.presentation.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.tasama.navigation.BottomNavItem
import com.example.tasama.presentation.ai.AIScreen
import com.example.tasama.presentation.chat.ChatScreen
import com.example.tasama.presentation.dashboard.DashboardScreen
import com.example.tasama.presentation.login.LoginScreen
import com.example.tasama.presentation.profile.ProfileScreen
import com.example.tasama.presentation.savings.SavingsScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel(),
    onGoogleSignInClick: () -> Unit = {}
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (authState) {
            is AuthState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "🦆", fontSize = 48.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tasama",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                LoginScreen(
                    onGoogleSignInClick = onGoogleSignInClick,
                    onLoginSuccess = {
                        // Managed by AuthState
                    }
                )
            }
            is AuthState.Authenticated -> {
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
                                    onTransactionClick = { /* Handle navigation */ }
                                )
                            }
                            composable(BottomNavItem.Savings.route) {
                                SavingsScreen()
                            }
                            composable(BottomNavItem.AIAdvisor.route) {
                                AIScreen(viewModel = koinViewModel())
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
        }
    }
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
