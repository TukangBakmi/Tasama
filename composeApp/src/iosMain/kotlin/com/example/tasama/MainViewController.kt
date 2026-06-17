package com.example.tasama

import androidx.compose.ui.window.ComposeUIViewController
import com.example.tasama.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) { App() }
