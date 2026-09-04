package com.example.tasama.presentation.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.repository.PresenceState
import com.example.tasama.presentation.components.PlatformBackHandler
import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.components.LocalTransientFeedbackHandler
import com.example.tasama.presentation.components.TransientFeedback
import com.example.tasama.presentation.main.LocalSnackbarHostState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    channelId: String,
    viewModel: ChatViewModel = koinViewModel(),
    onBackClick: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current
    val feedbackHandler = LocalTransientFeedbackHandler.current

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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

    Box(modifier = Modifier.fillMaxSize()) {
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

                                        var nowTime by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
                                        LaunchedEffect(Unit) {
                                            while (true) {
                                                kotlinx.coroutines.delay(30000) // Refresh every 30 seconds
                                                nowTime = Clock.System.now().toEpochMilliseconds()
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
                            IconButton(onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.exitSelectionMode()
                                } else {
                                    onBackClick()
                                }
                            }) {
                                Icon(
                                    imageVector = if (uiState.isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            if (uiState.isSelectionMode) {
                                IconButton(onClick = {
                                    val textToCopy = viewModel.getSelectedMessagesText()
                                    if (textToCopy.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(textToCopy))
                                        val count = uiState.selectedMessageIds.size
                                        val feedbackText = if (count == 1) "Message copied" else "$count messages copied"
                                        feedbackHandler(TransientFeedback.Copy(feedbackText))
                                    }
                                    viewModel.exitSelectionMode()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Selected")
                                }
                                IconButton(onClick = viewModel::showDeleteConfirmation) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                                }
                            } else {
                                var showMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Hapus Chat") },
                                        onClick = {
                                            showMenu = false
                                            // TODO: Implement clear history or similar if needed
                                            // For now just show delete confirmation for all messages if supported
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (uiState.isSelectionMode)
                                MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
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
                    textFieldValue = uiState.textFieldValue,
                    onValueChange = viewModel::onTextFieldValueChange,
                    onSend = viewModel::sendMessage,
                    replyingToMessage = uiState.replyingToMessage,
                    onCancelReply = { viewModel.setReplyingTo(null) },
                    isSending = uiState.isSending,
                    focusRequester = focusRequester
                )
            },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                ChatContent(
                    uiState = uiState,
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
                    onScrollToMessageComplete = viewModel::onScrollToMessageComplete
                )
            }
        }

        if (uiState.showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = viewModel::hideDeleteConfirmation,
                title = { Text("Delete selected messages?") },
                text = { Text("These messages will be removed for you. Others will still be able to see them.") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteSelectedMessages(feedbackHandler) },
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
    onScrollToMessageComplete: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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

    val showScrollToBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 10
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
            if (uiState.typingIndicatorText != null) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            uiState.typingIndicatorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                        DateHeader(date, modifier = Modifier.padding(horizontal = 12.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    val isSelected = uiState.selectedMessageIds.contains(message.id)
                    val isHighlighted = uiState.highlightedMessageId == message.id
                    MessageBubble(
                        message = message,
                        isSelected = isSelected,
                        isHighlighted = isHighlighted,
                        onLongClick = { onMessageLongClick(message.id) },
                        onClick = { onMessageClick(message.id) },
                        onSwipeToReply = { onSwipeToReply(message) },
                        onReplyClick = { repliedId, timestamp ->
                            onReplyClick(repliedId, timestamp)
                        }
                    )
                }
            }

            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                    scope.launch {
                        listState.animateScrollToItem(0)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onSwipeToReply: () -> Unit,
    onReplyClick: (String, Long?) -> Unit
) {
    val isUser = message.isFromMe
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    // Row highlight animation: fade-in (300ms) -> fade-out (1000ms)
    // The peak alpha is held while the message is centered (via ViewModel delay)
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 1f else 0f,
        animationSpec = if (isHighlighted) {
            tween(durationMillis = 300, easing = LinearOutSlowInEasing)
        } else {
            tween(durationMillis = 1000, easing = FastOutLinearInEasing)
        },
        label = "HighlightAlpha"
    )

    // Selection background is constant; Highlight background is animated.
    // Both span the entire width of the message row.
    val rowBackgroundAlpha = remember(isSelected, highlightAlpha) {
        if (isSelected) 0.18f else 0.15f * highlightAlpha
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
            .background(MaterialTheme.colorScheme.primary.copy(alpha = rowBackgroundAlpha))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = (offsetX.value + dragAmount).coerceIn(0f, 150f)
                        scope.launch {
                            offsetX.snapTo(newOffset)
                        }
                        if (newOffset >= 90f && !hasTriggeredHaptic) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            hasTriggeredHaptic = true
                        } else if (newOffset < 90f) {
                            hasTriggeredHaptic = false
                        }
                    },
                    onDragEnd = {
                        if (offsetX.value >= 90f) {
                            onSwipeToReply()
                        }
                        scope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                        }
                        hasTriggeredHaptic = false
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                        }
                        hasTriggeredHaptic = false
                    }
                )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        if (offsetX.value > 0) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 0.dp) // Aligned to the start of the 20dp padded area
                    .alpha((offsetX.value / 90f).coerceIn(0f, 1f)),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            shadowElevation = 0.5.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(shape)
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Column {
                    if (message.repliedMessageId != null) {
                        ReplyPreview(
                            senderName = message.repliedMessageSenderName ?: "Partner",
                            text = message.repliedMessageText ?: "",
                            modifier = Modifier.padding(bottom = 4.dp),
                            onReplyClick = { onReplyClick(message.repliedMessageId, 0L) }
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
                        if (isUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val isRead = message.readBy.filterKeys { it != message.userId }.isNotEmpty()
                            MessageStatusIcon(isRead = isRead, tint = contentColor.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(isRead: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val icon = if (isRead) Icons.Default.DoneAll else Icons.Default.Check
    val color = if (isRead) Color(0xFF34B7F1) else tint
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(12.dp),
        tint = color
    )
}

@Composable
fun DateHeader(date: kotlinx.datetime.LocalDate, modifier: Modifier = Modifier) {
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
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            if (replyingToMessage != null) {
                ReplyPreview(
                    senderName = if (replyingToMessage.isFromMe) "You" else replyingToMessage.senderName,
                    text = replyingToMessage.text,
                    onCancel = onCancelReply,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 1.dp
                ) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
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
                        maxLines = 4
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (textFieldValue.text.isNotBlank() && !isSending) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clickable(enabled = textFieldValue.text.isNotBlank() && !isSending, onClick = onSend),
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
    onReplyClick: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onReplyClick != null) { onReplyClick?.invoke() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
            if (onCancel != null) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                }
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
                    ChatMessage(id = "1", text = "Hey! How is our savings for the Japan trip going?", isFromMe = false),
                    ChatMessage(id = "2", text = "It's going well! We just reached 80% of our goal.", isFromMe = true),
                    ChatMessage(id = "3", text = "That's awesome! Let's save a bit more this month.", isFromMe = false)
                )
            ),
            onLoadMore = {}
        )
    }
}
