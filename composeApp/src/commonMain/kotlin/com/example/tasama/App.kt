package com.example.tasama

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.service.GeofenceMonitor
import com.example.tasama.presentation.main.MainScreen
import com.example.tasama.presentation.main.MainViewModel
import com.example.tasama.presentation.theme.TasamaTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalCoilApi::class)
@Composable
@Preview
fun App(
    initialTheme: AppTheme? = null,
    initialChannelId: String? = null,
    navigateToTab: String? = null,
    onChannelNavigated: () -> Unit = {},
    onTabNavigated: () -> Unit = {},
    onGoogleSignInClick: () -> Unit = {}
) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
            }
            .build()
    }

    val viewModel: MainViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsState()
    val isSystemDark = isSystemInDarkTheme()

    val isDarkTheme = remember(settings.theme, initialTheme, isSystemDark) {
        // Use initialTheme if settings haven't loaded yet (are still at default SYSTEM)
        val theme = if (settings.theme == AppTheme.SYSTEM && initialTheme != null) {
            initialTheme
        } else {
            settings.theme
        }
        
        when (theme) {
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
            AppTheme.SYSTEM -> isSystemDark
        }
    }

    TasamaTheme(darkTheme = isDarkTheme) {
        MainScreen(
            initialChannelId = initialChannelId,
            navigateToTab = navigateToTab,
            onChannelNavigated = onChannelNavigated,
            onTabNavigated = onTabNavigated,
            onGoogleSignInClick = onGoogleSignInClick
        )
    }
}
