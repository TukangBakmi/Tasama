package com.example.tasama.presentation.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender

@Composable
fun AIScreen(
    viewModel: AIViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    AIContent(
        uiState = uiState,
        onInputChange = viewModel::onInputChange,
        onSendClick = viewModel::sendMessage
    )
}

@Composable
fun AIContent(
    uiState: AIUiState,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(uiState.messages) { message ->
                ChatBubble(message)
            }

            if (uiState.isTyping) {
                item {
                    Text(
                        "Tasama AI sedang mengetik...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tanya Tasama AI...") },
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSendClick,
                enabled = uiState.inputText.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Kirim")
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(containerColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = contentColor,
                fontSize = 14.sp
            )
        }
    }
}

@Preview
@Composable
fun AIPreview() {
    MaterialTheme {
        AIContent(
            uiState = AIUiState(
                messages = listOf(
                    ChatMessage("1", "Halo! Ada yang bisa saya bantu?", MessageSender.AI),
                    ChatMessage("2", "Saya ingin mencatat pengeluaran makan siang 50rb", MessageSender.USER),
                    ChatMessage("3", "Baik, sudah saya catat ya!", MessageSender.AI)
                )
            ),
            onInputChange = {},
            onSendClick = {}
        )
    }
}
