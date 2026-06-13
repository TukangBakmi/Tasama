package com.example.tasama.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onTransactionClick: () -> Unit
) {

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Balance : Rp ${uiState.balance}")

        Spacer(modifier = Modifier.height(16.dp))

        Text("Income : Rp ${uiState.income}")

        Text("Expense : Rp ${uiState.expense}")

        Spacer(modifier = Modifier.height(24.dp))

        Text("Recent Transactions")

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {

            items(uiState.transactions) { transaction ->

                Text(
                    text = transaction.note
                )

            }
        }
    }

    Button(
        onClick = onTransactionClick
    ) {
        Text("Lihat Semua Transaksi")
    }
}