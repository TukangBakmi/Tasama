package com.example.tasama.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.model.MessageStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    channelId: String,
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(channelId) {
        viewModel.setChannel(channelId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shared Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Online", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        bottomBar = {
            ChatInput(
                message = uiState.inputText,
                onMessageChange = viewModel::onMessageChange,
                onSend = viewModel::sendMessage
            )
        }
    ) { paddingValues ->
        ChatContent(
            uiState = uiState,
            modifier = Modifier.padding(paddingValues),
            onLoadMore = viewModel::loadMoreMessages
        )
    }
}

@Composable
fun ChatContent(
    uiState: ChatUiState,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Detect when user scrolls to the top
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

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMoreMessages && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    // Auto-scroll to bottom only for new messages (not when loading more)
    var previousMessageCount by remember { mutableStateOf(uiState.messages.size) }
    LaunchedEffect(uiState.messages.size) {
        val newMessageCount = uiState.messages.size
        val isNewMessageAtBottom = newMessageCount > previousMessageCount && 
            uiState.messages.lastOrNull()?.isFromMe == true
        
        if (isNewMessageAtBottom || previousMessageCount == 0) {
            if (newMessageCount > 0) {
                listState.animateScrollToItem(newMessageCount - 1)
            }
        }
        previousMessageCount = newMessageCount
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            if (totalItemsCount == 0) return@derivedStateOf false
            
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisibleItem == null) return@derivedStateOf false
            
            val isLastItem = lastVisibleItem.index == totalItemsCount - 1
            if (!isLastItem) true 
            else {
                val viewportBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
                lastVisibleItem.offset + lastVisibleItem.size > viewportBottom
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (uiState.messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "💬",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No messages yet. Say hi!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    MessageBubble(message = message)
                }
            }
        }

        // Floating Action Button to scroll to bottom
        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        if (uiState.messages.isNotEmpty()) {
                            listState.animateScrollToItem(uiState.messages.size - 1)
                        }
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
fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!message.isFromMe) {
                val senderName = when (message.sender) {
                    MessageSender.AI -> "Sir Quack"
                    else -> message.senderName.ifEmpty { "User" }
                }
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = shape,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    val timeString = remember(message.timestamp) {
                        try {
                            val instant = Instant.fromEpochMilliseconds(message.timestamp)
                            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                            val hour = localDateTime.hour.toString().padStart(2, '0')
                            val minute = localDateTime.minute.toString().padStart(2, '0')
                            "$hour:$minute"
                        } catch (e: Exception) {
                            ""
                        }
                    }

                    if (timeString.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                        ) {
                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.6f)
                            )
                            if (message.isFromMe) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MessageStatusIcon(message.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus) {
    val icon = when (status) {
        MessageStatus.SENT -> Icons.Default.Check
        MessageStatus.DELIVERED, MessageStatus.READ -> Icons.Default.DoneAll
    }
    val tint = if (status == MessageStatus.READ) {
        Color(0xFF00BFFF) // Blue for read
    } else {
        LocalContentColor.current.copy(alpha = 0.5f)
    }
    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(14.dp),
        tint = tint
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape),
                placeholder = { Text("Type a message...") },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = message.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Preview
@Composable
fun ChatPreview() {
    MaterialTheme {
        ChatContent(
            uiState = ChatUiState(
                messages = listOf(
                    ChatMessage(id = "1", text = "Hey! How is our savings for the Japan trip going?", sender = MessageSender.USER, isFromMe = false),
                    ChatMessage(id = "2", text = "It's going well! We just reached 80% of our goal.", sender = MessageSender.USER, isFromMe = true),
                    ChatMessage(id = "3", text = "That's awesome! Let's save a bit more this month.", sender = MessageSender.USER, isFromMe = false)
                )
            ),
            onLoadMore = {}
        )
    }
}
