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
    initialChannelId: String? = null,
    navigateToPartner: Boolean = false,
    onChannelNavigated: () -> Unit = {},
    onPartnerNavigated: () -> Unit = {},
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

    val isDarkTheme = when (settings.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    TasamaTheme(darkTheme = isDarkTheme) {
        MainScreen(
            initialChannelId = initialChannelId,
            navigateToPartner = navigateToPartner,
            onChannelNavigated = onChannelNavigated,
            onPartnerNavigated = onPartnerNavigated,
            onGoogleSignInClick = onGoogleSignInClick
        )
    }
}
