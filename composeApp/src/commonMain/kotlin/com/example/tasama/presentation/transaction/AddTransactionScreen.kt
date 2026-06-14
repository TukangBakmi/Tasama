package com.example.tasama.presentation.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AddTransactionScreen(
    onSave: (Long, String) -> Unit
) {
    AddTransactionContent(onSave = onSave)
}

@Composable
fun AddTransactionContent(
    onSave: (Long, String) -> Unit
) {
    var amount by remember {
        mutableStateOf("")
    }

    var note by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        OutlinedTextField(
            value = amount,
            onValueChange = {
                amount = it
            },
            label = {
                Text("Amount")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it
            },
            label = {
                Text("Note")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val amountLong =
                    amount.toLongOrNull() ?: 0L

                onSave(
                    amountLong,
                    note
                )
            }
        ) {
            Text("Save")
        }
    }
}

@Preview
@Composable
fun AddTransactionPreview() {
    AddTransactionContent(onSave = { _, _ -> })
}