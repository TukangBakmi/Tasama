package com.example.tasama.presentation.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import com.example.tasama.presentation.chat.ChatListScreen
import com.example.tasama.presentation.chat.ChatScreen
import com.example.tasama.presentation.dashboard.DashboardScreen
import com.example.tasama.presentation.login.LoginScreen
import com.example.tasama.presentation.profile.ProfileScreen
import com.example.tasama.presentation.savings.SavingsScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import tasama.composeapp.generated.resources.Res
import tasama.composeapp.generated.resources.sir_quack

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
                            Image(
                                painter = painterResource(Res.drawable.sir_quack),
                                contentDescription = "Tasama Logo",
                                modifier = Modifier.fillMaxSize().padding(16.dp)
                            )
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
                    onLoginSuccess = {}
                )
            }
            is AuthState.Authenticated -> {
                val items = listOf(
                    BottomNavItem.Dashboard,
                    BottomNavItem.Savings,
                    BottomNavItem.Chat,
                    BottomNavItem.Profile
                )

                val pagerState = rememberPagerState(pageCount = { items.size })
                val scope = rememberCoroutineScope()

                MainContent(
                    navController = navController,
                    items = items,
                    pagerState = pagerState,
                    onTabClick = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    content = { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = "tabs",
                            modifier = Modifier.fillMaxSize().padding(padding)
                        ) {
                            composable("tabs") {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 2
                                ) { page ->
                                    when (items[page]) {
                                        BottomNavItem.Dashboard -> DashboardScreen(
                                            viewModel = koinViewModel(),
                                            onTransactionClick = {}
                                        )
                                        BottomNavItem.Savings -> SavingsScreen()
                                        BottomNavItem.Chat -> ChatListScreen(
                                            onChannelClick = { channelId ->
                                                navController.navigate("chat_room/$channelId")
                                            },
                                            onAIClick = {
                                                navController.navigate("ai_chat")
                                            }
                                        )
                                        BottomNavItem.Profile -> ProfileScreen(
                                            viewModel = koinViewModel()
                                        )
                                    }
                                }
                            }
                            composable("ai_chat") {
                                AIScreen(
                                    viewModel = koinViewModel(),
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                            composable("chat_room/{channelId}") { backStackEntry ->
                                val channelId = backStackEntry.savedStateHandle.get<String>("channelId") ?: ""
                                ChatScreen(
                                    channelId = channelId,
                                    onBackClick = { navController.popBackStack() }
                                )
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
    pagerState: PagerState? = null,
    onTabClick: ((Int) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isChatRoom = currentRoute?.startsWith("chat_room") == true

    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val showBottomBar = !isChatRoom && currentRoute != "ai_chat" && !isKeyboardVisible

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar {
                    items.forEachIndexed { index, item ->
                        val isSelected = pagerState?.currentPage == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != "tabs") {
                                    navController.navigate("tabs") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                onTabClick?.invoke(index)
                            },
                            icon = { Text(item.emoji) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        // Use the padding values from Scaffold to properly offset the content (mostly just bottom padding for the nav bar)
        Box(modifier = Modifier.padding(padding)) {
            content(PaddingValues(0.dp))
        }
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
                BottomNavItem.Chat,
                BottomNavItem.Profile
            ),
            content = { Text("Content Area") }
        )
    }
}