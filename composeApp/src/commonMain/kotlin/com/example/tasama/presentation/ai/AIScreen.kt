package com.example.tasama.presentation.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AIScreen() {
    AIContent()
}

@Composable
fun AIContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("AI Screen")
    }
}

@Preview
@Composable
fun AIPreview() {
    AIContent()
}
