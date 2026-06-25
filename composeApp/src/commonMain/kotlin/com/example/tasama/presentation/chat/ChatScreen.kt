package com.example.tasama.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.model.MessageStatus
import com.example.tasama.presentation.main.LocalSnackbarHostState
import kotlinx.datetime.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    channelId: String,
    viewModel: ChatViewModel = koinViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(channelId) {
        viewModel.setChannel(channelId)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { uiState.error }
            .filterNotNull()
            .collect { error ->
                viewModel.clearError()
                snackbarHostState.showSnackbar(error)
            }
    }

    Scaffold(
        topBar = {
            Column {
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
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.channelName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    uiState.channelName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                
                                var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
                                LaunchedEffect(Unit) {
                                    while (true) {
                                        kotlinx.coroutines.delay(30000) // Refresh every 30 seconds
                                        now = Clock.System.now().toEpochMilliseconds()
                                    }
                                }

                                val statusText = remember(uiState.otherUser?.lastActive, now) {
                                    val lastActive = uiState.otherUser?.lastActive ?: 0L
                                    if (now - lastActive < 10000) {
                                        "online"
                                    } else {
                                        try {
                                            val instant = Instant.fromEpochMilliseconds(lastActive)
                                            val tz = TimeZone.currentSystemDefault()
                                            val lastActiveDateTime = instant.toLocalDateTime(tz)
                                            val nowDateTime = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz)
                                            
                                            val timeStr = "${lastActiveDateTime.hour.toString().padStart(2, '0')}:${lastActiveDateTime.minute.toString().padStart(2, '0')}"

                                            when (lastActiveDateTime.date) {
                                                nowDateTime.date -> {
                                                    "last seen today at $timeStr"
                                                }
                                                nowDateTime.date.minus(DatePeriod(days = 1)) -> {
                                                    "last seen yesterday at $timeStr"
                                                }
                                                else -> {
                                                    val day = lastActiveDateTime.day.toString().padStart(2, '0')
                                                    val month = lastActiveDateTime.month.number.toString().padStart(2, '0')
                                                    val year = lastActiveDateTime.year
                                                    "last seen $day/$month/$year"
                                                }
                                            }
                                        } catch (_: Exception) {
                                            "offline"
                                        }
                                    }
                                }

                                if (statusText.isNotEmpty()) {
                                    Text(
                                        statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (statusText == "online") MaterialTheme.colorScheme.primary 
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        },
        bottomBar = {
            ChatInput(
                message = uiState.inputText,
                onMessageChange = viewModel::onMessageChange,
                onSend = viewModel::sendMessage
            )
        },
        contentWindowInsets = WindowInsets(0)
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

            val lastVisibleItem =
                layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false

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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                items(uiState.messages.size, key = { uiState.messages[it].id }) { index ->
                    val message = uiState.messages[index]
                    val date = Instant.fromEpochMilliseconds(message.timestamp)
                        .toLocalDateTime(TimeZone.currentSystemDefault()).date
                    
                    val showHeader = if (index == 0) true else {
                        val prevDate = Instant.fromEpochMilliseconds(uiState.messages[index - 1].timestamp)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                        date != prevDate
                    }
                    
                    Column {
                        if (showHeader) {
                            DateHeader(date)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        MessageBubble(message = message)
                    }
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
                        val totalItems = listState.layoutInfo.totalItemsCount
                        if (totalItems > 0) {
                            listState.animateScrollToItem(totalItems - 1)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
    
    // WhatsApp-like colors (keeping your theme colors but using them in a similar way)
    val containerColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    // WhatsApp bubble shape
    val shape = if (message.isFromMe) {
        RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
    } else {
        RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            shadowElevation = 0.5.dp,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    
                    val timeString = remember(message.timestamp) {
                        try {
                            val instant = Instant.fromEpochMilliseconds(message.timestamp)
                            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                            val hour = localDateTime.hour.toString().padStart(2, '0')
                            val minute = localDateTime.minute.toString().padStart(2, '0')
                            "$hour:$minute"
                        } catch (_: Exception) {
                            ""
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (timeString.isNotEmpty()) {
                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
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

@Composable
fun DateHeader(date: LocalDate, modifier: Modifier = Modifier) {
    val dateString = remember(date) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        when (date) {
            now -> "Today"
            now.minus(DatePeriod(days = 1)) -> "Yesterday"
            else -> {
                val day = date.day.toString().padStart(2, '0')
                val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                val year = if (date.year != now.year) " ${date.year}" else ""
                "$day $month$year"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = dateString,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
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
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Could add emoji picker button here
                    TextField(
                        value = message,
                        onValueChange = onMessageChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.isNotBlank()) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    .clickable(enabled = message.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
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
