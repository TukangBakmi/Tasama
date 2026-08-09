package com.example.tasama.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.presentation.components.UserAvatar
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
    onBackClick: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setResumed(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setResumed(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                        if (uiState.isSelectionMode) {
                            Text("${uiState.selectedMessageIds.size} selected")
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        uiState.otherUser?.let { onUserClick(it.id) }
                                    }
                            ) {
                                UserAvatar(
                                    user = uiState.otherUser,
                                    modifier = Modifier.size(36.dp),
                                    fallbackName = uiState.channelName
                                )
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

                                    val statusText = remember(uiState.otherUser, now) {
                                        val lastActive = uiState.otherUser?.lastActive ?: 0L
                                        if (lastActive == 0L) return@remember ""
                                        if (now - lastActive < 30000) {
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
                                            color = if (statusText == "online") {
                                                if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                }
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (uiState.isSelectionMode) {
                            IconButton(onClick = viewModel::exitSelectionMode) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        } else {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (uiState.isSelectionMode) {
                            IconButton(onClick = viewModel::showDeleteConfirmation) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (uiState.isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
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
            if (!uiState.isSelectionMode) {
                ChatInput(
                    message = uiState.inputText,
                    onMessageChange = viewModel::onMessageChange,
                    onSend = viewModel::sendMessage,
                    replyingToMessage = uiState.replyingToMessage,
                    onCancelReply = { viewModel.setReplyingTo(null) },
                    isSending = uiState.isSending,
                    focusRequester = focusRequester
                )
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        ChatContent(
            uiState = uiState,
            modifier = Modifier.padding(paddingValues),
            onLoadMore = viewModel::loadMoreMessages,
            onMessageLongClick = viewModel::enterSelectionMode,
            onMessageClick = { messageId ->
                if (uiState.isSelectionMode) {
                    viewModel.toggleMessageSelection(messageId)
                }
            },
            onSwipeToReply = { message ->
                viewModel.setReplyingTo(message)
                focusRequester.requestFocus()
                keyboardController?.show()
            },
            onReplyClick = { repliedId, timestamp ->
                viewModel.jumpToMessage(repliedId, timestamp)
            },
            onScrollToMessageComplete = viewModel::onScrollToMessageComplete
        )
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::hideDeleteConfirmation,
            title = { Text("Delete selected messages?") },
            text = { Text("These messages will be removed for you. Others will still be able to see them.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deleteSelectedMessages,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideDeleteConfirmation) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatContent(
    uiState: ChatUiState,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    onMessageLongClick: (String) -> Unit = {},
    onMessageClick: (String) -> Unit = {},
    onSwipeToReply: (ChatMessage) -> Unit = {},
    onReplyClick: (String, Long?) -> Unit = { _, _ -> },
    onScrollToMessageComplete: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Show button if we are not at the bottom
    val showScrollToBottom by remember {
        derivedStateOf {
            // In reverseLayout, index 0 is the bottom message. 
            // Show if index 0 is not the first visible or if it's partially scrolled off
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 10
        }
    }

    // We reverse the list for the UI
    val reversedMessages = remember(uiState.messages) {
        uiState.messages.asReversed()
    }

    // Auto-scroll to bottom when keyboard opens
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isImeVisible) {
        // Only auto-scroll if keyboard is opening AND user is already near the bottom
        if (isImeVisible && reversedMessages.isNotEmpty() && listState.firstVisibleItemIndex < 2) {
            listState.animateScrollToItem(0)
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(reversedMessages.firstOrNull()?.id) {
        if (reversedMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Detect when user scrolls to the "top" (which is now the end of the list)
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()

            if (lastVisibleItem == null) false
            else {
                // In reverseLayout, the end of the list is the top of the chat history
                lastVisibleItem.index == totalItemsNumber - 1
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMoreMessages && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    val haptic = LocalHapticFeedback.current

    // Observe scroll requests to jump to a message
    LaunchedEffect(uiState.scrollToMessageId, reversedMessages) {
        uiState.scrollToMessageId?.let { targetId ->
            val index = reversedMessages.indexOfFirst { it.id == targetId }
            if (index != -1) {
                // To center the item, we need to know its index and the viewport height
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                
                // Find if the item is already visible to estimate its height
                val targetItem = layoutInfo.visibleItemsInfo.find { it.key == targetId }
                val itemHeightEstimate = targetItem?.size ?: 60 // fallback estimate

                // Offset to place item at the center: (viewportHeight - itemHeight) / 2
                val centerOffset = (viewportHeight - itemHeightEstimate) / 2
                
                listState.animateScrollToItem(index, -centerOffset)
                onScrollToMessageComplete()
            }
        }
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
    ) {
        if (uiState.messages.isEmpty()) {
            // ... Empty state remains same
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true, // Key fix: Anchor to bottom
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
            ) {
                items(
                    count = reversedMessages.size,
                    key = { reversedMessages[it].id }
                ) { index ->
                    val message = reversedMessages[index]
                    val date = Instant.fromEpochMilliseconds(message.timestamp)
                        .toLocalDateTime(TimeZone.currentSystemDefault()).date

                    // In reverseLayout, index 0 is bottom.
                    // Header shows if it's the last message in the reversed list (top of chat)
                    // or if the message "above" it (index + 1) is a different date.
                    val showHeader = if (index == reversedMessages.size - 1) true else {
                        val nextDate = Instant.fromEpochMilliseconds(reversedMessages[index + 1].timestamp)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                        date != nextDate
                    }

                    Column {
                        if (showHeader) {
                            DateHeader(date)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val isSelected = uiState.selectedMessageIds.contains(message.id)
                        val isHighlighted = uiState.highlightedMessageId == message.id
                        MessageBubble(
                            message = message,
                            isSelected = isSelected,
                            isHighlighted = isHighlighted,
                            isSelectionMode = uiState.isSelectionMode,
                            onLongClick = { onMessageLongClick(message.id) },
                            onClick = { onMessageClick(message.id) },
                            onSwipeToReply = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwipeToReply(it)
                            },
                            onReplyClick = { repliedId, timestamp ->
                                onReplyClick(repliedId, timestamp)
                            }
                        )
                    }
                }

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
            }
        }

        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.size(42.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isSelected: Boolean = false,
    isHighlighted: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onSwipeToReply: (ChatMessage) -> Unit = {},
    onReplyClick: (String, Long?) -> Unit = { _, _ -> }
) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    
    // Swipe to reply logic
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 60.dp.toPx() }
    
    // WhatsApp-like colors
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
    
    val shape = if (message.isFromMe) {
        RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
    } else {
        RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)
    }

    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val animatedHighlightColor by animateColorAsState(
        targetValue = if (isHighlighted) highlightColor else Color.Transparent,
        animationSpec = tween(
            durationMillis = if (isHighlighted) 800 else 800,
            easing = FastOutSlowInEasing
        ),
        label = "highlight"
    )

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        animatedHighlightColor
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .draggable(
                state = rememberDraggableState { delta ->
                    if (!isSelectionMode) {
                        scope.launch {
                            val newOffset = (offset.value + delta).coerceIn(0f, swipeThreshold * 1.5f)
                            offset.snapTo(newOffset)
                        }
                    }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (offset.value >= swipeThreshold) {
                        onSwipeToReply(message)
                    }
                    scope.launch {
                        offset.animateTo(0f)
                    }
                }
            )
            .pointerInput(isSelectionMode) {
                detectTapGestures(
                    onLongPress = { if (!isSelectionMode) onLongClick() },
                    onTap = { if (isSelectionMode) onClick() }
                )
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        // Reply Icon that appears when swiping
        if (offset.value > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .offset(x = with(density) { (offset.value - swipeThreshold).toDp().coerceAtMost(0.dp) }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Surface(
            color = if (isHighlighted) containerColor.copy(alpha = 0.9f) else containerColor,
            contentColor = contentColor,
            shape = shape,
            shadowElevation = if (isHighlighted) 4.dp else 0.5.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .offset(x = with(density) { offset.value.toDp() })
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Column {
                    if (message.repliedMessageId != null) {
                        ReplyPreview(
                            senderName = message.repliedMessageSenderName ?: "Unknown",
                            text = message.repliedMessageText ?: "",
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .clickable { 
                                    onReplyClick(
                                        message.repliedMessageId,
                                        message.repliedMessageTimestamp
                                    ) 
                                }
                        )
                    }

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
                            val isRead = message.readBy.containsKey(message.receiverId)
                            val isDelivered = message.deliveredTo.containsKey(message.receiverId)
                            MessageStatusIcon(isRead = isRead, isDelivered = isDelivered)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(isRead: Boolean, isDelivered: Boolean, modifier: Modifier = Modifier) {
    val icon = if (isDelivered || isRead) Icons.Default.DoneAll else Icons.Default.Check
    val tint = if (isRead) {
        Color(0xFF00BFFF) // Blue for read
    } else {
        LocalContentColor.current.copy(alpha = 0.5f)
    }
    Icon(
        imageVector = icon,
        contentDescription = if (isRead) "Read" else if (isDelivered) "Delivered" else "Sent",
        modifier = modifier.size(14.dp),
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
    replyingToMessage: ChatMessage? = null,
    onCancelReply: () -> Unit = {},
    isSending: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            AnimatedVisibility(
                visible = replyingToMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (replyingToMessage != null) {
                    ReplyPreview(
                        senderName = if (replyingToMessage.isFromMe) "You" else replyingToMessage.senderName ?: "Unknown",
                        text = replyingToMessage.text,
                        onCloseClick = onCancelReply,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            Row(
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
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            placeholder = { Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Default
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            maxLines = 4,
                            enabled = true
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (message.isNotBlank() && !isSending) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clickable(enabled = message.isNotBlank() && !isSending, onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
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
    }
}

@Composable
fun ReplyPreview(
    senderName: String,
    text: String,
    modifier: Modifier = Modifier,
    onCloseClick: (() -> Unit)? = null
) {
    val displaySender = if (senderName.isBlank()) "Unknown" else senderName
    val displayText = if (text.isBlank()) "Message not available" else text

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .weight(1f)
        ) {
            Text(
                text = displaySender,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onCloseClick != null) {
            IconButton(onClick = onCloseClick) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel Reply",
                    modifier = Modifier.size(18.dp)
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
