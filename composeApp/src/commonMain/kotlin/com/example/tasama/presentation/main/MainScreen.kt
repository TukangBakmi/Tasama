package com.example.tasama.presentation.main

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.*
import com.example.tasama.navigation.BottomNavItem
import com.example.tasama.presentation.ai.AIScreen
import com.example.tasama.presentation.chat.ChatListScreen
import com.example.tasama.presentation.chat.ChatScreen
import com.example.tasama.presentation.dashboard.DashboardScreen
import com.example.tasama.presentation.login.LoginScreen
import com.example.tasama.presentation.partner.PartnerScreen
import com.example.tasama.presentation.profile.ProfileScreen
import com.example.tasama.presentation.savings.SavingsScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import tasama.composeapp.generated.resources.Res
import tasama.composeapp.generated.resources.logo

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
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .shadow(8.dp, androidx.compose.foundation.shape.CircleShape)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.logo),
                                contentDescription = "Tasama Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(1.1f),
                                contentScale = ContentScale.Crop
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
                    BottomNavItem.Partner,
                    BottomNavItem.Profile
                )

                val pagerState = rememberPagerState(pageCount = { items.size })

                NavHost(
                    navController = navController,
                    startDestination = "tabs",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(
                        route = "tabs",
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                        }
                    ) {
                        val scope = rememberCoroutineScope()
                        
                        Scaffold(
                            bottomBar = {
                                val density = LocalDensity.current
                                val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
                                
                                AnimatedVisibility(
                                    visible = !isKeyboardVisible,
                                    enter = slideInVertically(initialOffsetY = { it }),
                                    exit = slideOutVertically(targetOffsetY = { it })
                                ) {
                                    NavigationBar {
                                        items.forEachIndexed { index, item ->
                                            val isSelected = pagerState.currentPage == index
                                            NavigationBarItem(
                                                selected = isSelected,
                                                onClick = {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(index)
                                                    }
                                                },
                                                icon = { Text(item.emoji) },
                                                label = { Text(item.title) }
                                            )
                                        }
                                    }
                                }
                            },
                            contentWindowInsets = WindowInsets(0)
                        ) { padding ->
                            val currentItem = items[pagerState.currentPage]
                            val isGuest = (authState as? AuthState.Authenticated)?.isGuest == true
                            
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 0,
                                userScrollEnabled = if (currentItem == BottomNavItem.Partner) {
                                    isGuest
                                } else true
                            ) { page ->
                                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                                    when (items[page]) {
                                        BottomNavItem.Dashboard -> DashboardScreen(onTransactionClick = {})
                                        BottomNavItem.Savings -> SavingsScreen()
                                        BottomNavItem.Chat -> ChatListScreen(
                                            onChannelClick = { channelId ->
                                                navController.navigate("chat_room/$channelId")
                                            },
                                            onAIClick = {
                                                navController.navigate("ai_chat")
                                            }
                                        )
                                        BottomNavItem.Partner -> PartnerScreen()
                                        BottomNavItem.Profile -> ProfileScreen()
                                    }
                                }
                            }
                        }
                    }
                    composable(
                        route = "ai_chat",
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                        }
                    ) {
                        AIScreen(
                            viewModel = koinViewModel(),
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "chat_room/{channelId}",
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                        }
                    ) { backStackEntry ->
                        val channelId = backStackEntry.savedStateHandle.get<String>("channelId") ?: ""
                        ChatScreen(
                            channelId = channelId,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun MainPreview() {
    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { Text("🏠") },
                        label = { Text("Home") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                Text("Content Area")
            }
        }
    }
}
