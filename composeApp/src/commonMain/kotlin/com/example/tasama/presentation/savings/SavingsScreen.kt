package com.example.tasama.presentation.savings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SavingsScreen() {
    SavingsContent()
}

@Composable
fun SavingsContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Savings Screen")
    }
}

@Preview
@Composable
fun SavingsPreview() {
    SavingsContent()
}
