package com.example.tasama.presentation.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TransactionListScreen(
    viewModel: TransactionViewModel,
    onAddClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    TransactionListContent(
        uiState = uiState,
        onAddClick = onAddClick
    )
}

@Composable
fun TransactionListContent(
    uiState: TransactionUiState,
    onAddClick: () -> Unit
) {
    Column {
        Button(
            onClick = onAddClick
        ) {
            Text("Tambah Transaksi")
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(uiState.transactions) { transaction ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        Text(transaction.note)
                        Text("Rp ${transaction.amount}")
                        Text(transaction.category)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun TransactionListPreview() {
    TransactionListContent(
        uiState = TransactionUiState(
            transactions = emptyList()
        ),
        onAddClick = {}
    )
}
