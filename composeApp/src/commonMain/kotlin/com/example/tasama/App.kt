package com.example.tasama

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview

import com.example.tasama.data.repository.FakeTransactionRepository
import com.example.tasama.navigation.AppNavigation
import com.example.tasama.presentation.dashboard.DashboardViewModel

@Composable
@Preview
fun App() {

    val viewModel = remember {
        DashboardViewModel(
            FakeTransactionRepository()
        )
    }

    MaterialTheme {

        AppNavigation()

    }
}