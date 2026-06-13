package com.example.tasama.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tasama.data.repository.FakeTransactionRepository
import com.example.tasama.presentation.dashboard.DashboardScreen
import com.example.tasama.presentation.dashboard.DashboardViewModel
import com.example.tasama.presentation.transaction.AddTransactionScreen
import com.example.tasama.presentation.transaction.TransactionListScreen
import com.example.tasama.presentation.transaction.TransactionViewModel

@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    val repository = remember {
        FakeTransactionRepository()
    }

    val dashboardViewModel = remember {
        DashboardViewModel(repository)
    }

    val transactionViewModel = remember {
        TransactionViewModel(repository)
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {

        composable(
            Screen.Dashboard.route
        ) {

            DashboardScreen(
                viewModel = dashboardViewModel,
                onTransactionClick = {
                    navController.navigate(
                        Screen.TransactionList.route
                    )
                }
            )
        }

        composable(
            Screen.TransactionList.route
        ) {

            TransactionListScreen(
                viewModel = transactionViewModel,
                onAddClick = {
                    navController.navigate(
                        Screen.AddTransaction.route
                    )
                }
            )
        }

        composable(
            Screen.AddTransaction.route
        ) {

            AddTransactionScreen(
                onSave = { amount, note ->

                    println(
                        "Amount=$amount Note=$note"
                    )

                    navController.popBackStack()
                }
            )
        }
    }
}