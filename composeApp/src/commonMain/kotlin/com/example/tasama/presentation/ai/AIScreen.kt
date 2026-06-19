package com.example.tasama.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import tasama.composeapp.generated.resources.Res
import tasama.composeapp.generated.resources.sir_quack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIScreen(
    viewModel: AIViewModel,
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.sir_quack),
                            contentDescription = "Sir Quack",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Sir Quack",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            "AI Advisor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        AIContent(
            uiState = uiState,
            onLoadMore = viewModel::loadMoreMessages,
            modifier = Modifier.weight(1f)
        )

        AIInput(
            message = uiState.inputText,
            onMessageChange = viewModel::onInputChange,
            onSend = viewModel::sendMessage
        )
    }
}

@Composable
fun AIInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tanya Sir Quack...") },
                shape = RoundedCornerShape(16.dp),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = message.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Kirim")
            }
        }
    }
}

@Composable
fun AIContent(
    uiState: AIUiState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) false
            else {
                val firstVisibleItem = visibleItemsInfo.first()
                firstVisibleItem.index == 0 && firstVisibleItem.offset >= 0
            }
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.isTyping) {
        val totalItems = uiState.messages.size + (if (uiState.isTyping) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMoreMessages && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            if (totalItemsCount == 0) return@derivedStateOf false
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            val isLastItem = lastVisibleItem.index == totalItemsCount - 1
            if (!isLastItem) true
            else {
                val viewportBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
                lastVisibleItem.offset + lastVisibleItem.size > viewportBottom
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
        ) {
            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            items(uiState.messages, key = { it.id }) { message ->
                ChatBubble(message)
            }

            if (uiState.isTyping) {
                item {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sir Quack sedang mengetik...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val totalItems = uiState.messages.size + (if (uiState.isTyping) 1 else 0)
                        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom"
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(containerColor)
                .padding(12.dp)
        ) {
            Text(text = message.text, color = contentColor, fontSize = 14.sp)
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
                    ChatMessage(id = "1", text = "Halo! Ada yang bisa saya bantu?", sender = MessageSender.AI),
                    ChatMessage(id = "2", text = "Saya ingin mencatat pengeluaran makan siang 50rb", sender = MessageSender.USER),
                    ChatMessage(id = "3", text = "Baik, sudah saya catat ya!", sender = MessageSender.AI)
                )
            ),
            onLoadMore = {}
        )
    }
}