package com.example.tasama.presentation.components

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS doesn't have a hardware back button, so this is a no-op
}
