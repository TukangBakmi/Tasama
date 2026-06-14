package com.example.tasama

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview

import com.example.tasama.data.repository.FakeTransactionRepository
import com.example.tasama.navigation.AppNavigation
import com.example.tasama.presentation.dashboard.DashboardScreen
import com.example.tasama.presentation.dashboard.DashboardViewModel
import com.example.tasama.presentation.main.MainScreen

@Composable
@Preview
fun App() {

    val dashboardViewModel = remember {
        DashboardViewModel(
            FakeTransactionRepository()
        )
    }

    MainScreen(
        homeContent = {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onTransactionClick = {}
            )
        }
    )
}