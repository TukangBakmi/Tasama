package com.example.tasama.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.repository.PresenceState
import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.components.PlatformBackHandler
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    channelId: String,
    viewModel: ChatViewModel = koinViewModel(),
    onBackClick: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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

    PlatformBackHandler(enabled = uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    LaunchedEffect(Unit) {
        snapshotFlow { uiState.error }
            .filterNotNull()
            .collect { error ->
                viewModel.clearError()
                snackbarHostState.showSnackbar(error)
            }
    }

    var hasRestoredDraft by remember(channelId) { mutableStateOf(false) }
    LaunchedEffect(uiState.inputText) {
        if (uiState.inputText.isNotEmpty() && !hasRestoredDraft) {
            hasRestoredDraft = true
            // Restore cursor position to end when draft is loaded
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (uiState.isSelectionMode) {
                            Text("${uiState.selectedMessageIds.size} terpilih")
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

                                    var nowTime by remember { mutableLongStateOf(kotlin.time.Clock.System.now().toEpochMilliseconds()) }
                                    LaunchedEffect(Unit) {
                                        while (true) {
                                            kotlinx.coroutines.delay(30000) // Refresh every 30 seconds
                                            nowTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
                                        }
                                    }

                                    val statusText = remember(uiState.presence, nowTime) {
                                        when (val presence = uiState.presence) {
                                            is PresenceState.Online -> "online"
                                            is PresenceState.Offline -> {
                                                val lastSeen = presence.lastSeen
                                                if (lastSeen == 0L) return@remember ""
                                                try {
                                                    val instant = Instant.fromEpochMilliseconds(lastSeen)
                                                    val tz = TimeZone.currentSystemDefault()
                                                    val lastActiveDateTime = instant.toLocalDateTime(tz)
                                                    val nowDateTime = Instant.fromEpochMilliseconds(nowTime).toLocalDateTime(tz)

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
                                                            val month = lastActiveDateTime.month.ordinal.plus(1).toString().padStart(2, '0')
                                                            val year = lastActiveDateTime.year
                                                            "last seen $day/$month/$year"
                                                        }
                                                    }
                                                } catch (_: Exception) {
                                                    "offline"
                                                }
                                            }
                                        }
                                    }

                                    if (statusText.isNotEmpty()) {
                                        Text(
                                            statusText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (uiState.isSelectionMode) {
                            IconButton(onClick = {
                                val selectedMessages = uiState.messages.filter { uiState.selectedMessageIds.contains(it.id) }
                                // Handle copy if needed
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = viewModel::showDeleteConfirmation) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (uiState.isSelectionMode) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            onReplyClick = { repliedId, _ ->
                viewModel.jumpToMessage(repliedId)
            },
            onScrollToMessageComplete = viewModel::onScrollToMessageComplete,
            textFieldValue = uiState.textFieldValue,
            onValueChange = viewModel::onTextFieldValueChange,
            onSend = viewModel::sendMessage,
            onCancelReply = { viewModel.setReplyingTo(null) },
            focusRequester = focusRequester
        )

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
    onScrollToMessageComplete: () -> Unit = {},
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
    focusRequester: FocusRequester
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Show button if we are not at the bottom
    val showScrollToBottom by remember {
        derivedStateOf {
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
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                
                val targetItem = layoutInfo.visibleItemsInfo.find { it.key == targetId }
                val itemHeightEstimate = targetItem?.size ?: 60

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
            // Empty state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
            ) {
                if (uiState.typingUsers.isNotEmpty()) {
                    item(key = "typing_indicator") {
                        TypingBubble()
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                items(
                    count = reversedMessages.size,
                    key = { index -> reversedMessages[index].id }
                ) { index ->
                    val message = reversedMessages[index]
                    val date = Instant.fromEpochMilliseconds(message.timestamp)
                        .toLocalDateTime(TimeZone.currentSystemDefault()).date

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
                .padding(bottom = 80.dp, end = 16.dp) // Adjusted to be above input
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.size(42.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
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

        ChatInput(
            textFieldValue = textFieldValue,
            onValueChange = onValueChange,
            onSend = onSend,
            replyingToMessage = uiState.replyingToMessage,
            onCancelReply = onCancelReply,
            isSending = uiState.isSending,
            focusRequester = focusRequester,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isSelected: Boolean,
    isHighlighted: Boolean,
    isSelectionMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onSwipeToReply: (ChatMessage) -> Unit,
    onReplyClick: (String, Long?) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isHighlighted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else -> Color.Transparent
        }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
        val bubbleColor = if (message.isFromMe) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        val contentColor = if (message.isFromMe) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        Column(
            modifier = Modifier.align(alignment).widthIn(max = 300.dp)
        ) {
            Surface(
                color = bubbleColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isFromMe) 16.dp else 4.dp,
                    bottomEnd = if (message.isFromMe) 4.dp else 16.dp
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (message.repliedMessageId != null) {
                        ReplyPreview(
                            senderName = message.repliedMessageSenderName ?: "Partner",
                            text = message.repliedMessageText ?: "",
                            modifier = Modifier.padding(bottom = 4.dp),
                            onReplyClick = { onReplyClick(message.repliedMessageId, 0L) }
                        )
                    }
                    Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                    
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val time = Instant.fromEpochMilliseconds(message.timestamp)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        Text(
                            text = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        if (message.isFromMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val isRead = message.readBy.filterKeys { it != message.userId }.isNotEmpty()
                            MessageStatusIcon(isRead = isRead, isDelivered = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(isRead: Boolean, isDelivered: Boolean, modifier: Modifier = Modifier) {
    val icon = if (isRead) Icons.Default.DoneAll else Icons.Default.Check
    val color = if (isRead) Color(0xFF34B7F1) else LocalContentColor.current.copy(alpha = 0.5f)
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(14.dp),
        tint = color
    )
}

@Composable
fun DateHeader(date: LocalDate, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val text = when (date) {
                now -> "Today"
                now.minus(DatePeriod(days = 1)) -> "Yesterday"
                else -> "${date.day} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
            }
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChatInput(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    replyingToMessage: ChatMessage?,
    onCancelReply: () -> Unit,
    isSending: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (replyingToMessage != null) {
                ReplyPreview(
                    senderName = if (replyingToMessage.isFromMe) "You" else "Partner",
                    text = replyingToMessage.text,
                    onCancel = onCancelReply,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Message") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { if (textFieldValue.text.isNotBlank() && !isSending) onSend() },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
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
    onReplyClick: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onReplyClick != null) { onReplyClick?.invoke() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onCancel != null) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun TypingBubble(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot1Alpha), CircleShape))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot2Alpha), CircleShape))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot3Alpha), CircleShape))
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
            onLoadMore = {},
            textFieldValue = TextFieldValue(""),
            onValueChange = {},
            onSend = {},
            onCancelReply = {},
            focusRequester = FocusRequester()
        )
    }
}
