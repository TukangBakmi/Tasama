package com.example.tasama

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.tasama.di.initKoin
import com.example.tasama.presentation.main.MainScreen
import org.koin.compose.KoinContext

@Composable
@Preview
fun App() {
    initKoin()
    KoinContext {
        MaterialTheme {
            MainScreen()
        }
    }
}
