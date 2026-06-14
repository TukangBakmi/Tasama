package com.example.tasama.presentation.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ChatScreen() {
    ChatContent()
}

@Composable
fun ChatContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Chat with Partner Screen")
    }
}

@Preview
@Composable
fun ChatPreview() {
    ChatContent()
}
