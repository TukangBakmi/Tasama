package com.example.tasama

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.tasama.di.appModule
import com.example.tasama.presentation.main.MainScreen
import org.koin.compose.KoinContext
import org.koin.core.context.startKoin

@Composable
@Preview
fun App() {
    KoinContext {
        MaterialTheme {
            MainScreen()
        }
    }
}
