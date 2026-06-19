package com.example.tasama.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.*
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
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        },
        bottomBar = {
            AIInput(
                message = uiState.inputText,
                onMessageChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage
            )
        }
    ) { paddingValues ->
        AIContent(
            uiState = uiState,
            onLoadMore = viewModel::loadMoreMessages,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIInput(
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
                    TextField(
                        value = message,
                        onValueChange = onMessageChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tanya Sir Quack...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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

    // Auto-scroll logic matching ChatScreen
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

    // Also scroll when AI starts typing
    LaunchedEffect(uiState.isTyping) {
        if (uiState.isTyping) {
            val totalItems = uiState.messages.size + 1
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
                val date = kotlinx.datetime.Instant.fromEpochMilliseconds(message.timestamp)
                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                
                val showHeader = if (index == 0) true else {
                    val prevDate = kotlinx.datetime.Instant.fromEpochMilliseconds(uiState.messages[index - 1].timestamp)
                        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                    date != prevDate
                }
                
                Column {
                    if (showHeader) {
                        DateHeader(date)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    MessageBubble(message)
                }
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
    val isUser = message.isFromMe
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val shape = if (isUser) {
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
                            val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(message.timestamp)
                            val localDateTime = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                            val hour = localDateTime.hour.toString().padStart(2, '0')
                            val minute = localDateTime.minute.toString().padStart(2, '0')
                            "$hour:$minute"
                        } catch (e: Exception) {
                            ""
                        }
                    }

                    if (timeString.isNotEmpty()) {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: kotlinx.datetime.LocalDate, modifier: Modifier = Modifier) {
    val dateString = remember(date) {
        val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
        when (date) {
            now -> "Today"
            now.minus(kotlinx.datetime.DatePeriod(days = 1)) -> "Yesterday"
            else -> {
                val day = date.dayOfMonth.toString().padStart(2, '0')
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