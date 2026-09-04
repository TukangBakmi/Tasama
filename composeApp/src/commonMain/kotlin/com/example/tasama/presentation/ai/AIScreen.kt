package com.example.tasama.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.example.tasama.presentation.components.PlatformBackHandler
import com.example.tasama.presentation.components.TransientFeedbackOverlay
import com.example.tasama.util.formatCurrency
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import tasama.composeapp.generated.resources.Res
import tasama.composeapp.generated.resources.sir_quack
import com.example.tasama.presentation.components.LocalTransientFeedbackHandler
import com.example.tasama.presentation.components.LocalTransientFeedback
import com.example.tasama.presentation.components.LocalTransientFeedbackActionHandler
import com.example.tasama.presentation.components.TransientFeedback
import com.example.tasama.presentation.main.LocalSnackbarHostState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIScreen(
    viewModel: AIViewModel,
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current
    val feedbackHandler = LocalTransientFeedbackHandler.current
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { uiState.error }
            .filterNotNull()
            .collect { error ->
                viewModel.clearError()
                snackbarHostState.showSnackbar(error)
            }
    }

    PlatformBackHandler(enabled = uiState.selectedMessageIds.isNotEmpty()) {
        viewModel.clearSelection()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            if (uiState.selectedMessageIds.isNotEmpty()) {
                                Text("${uiState.selectedMessageIds.size} terpilih")
                            } else {
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
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (uiState.selectedMessageIds.isNotEmpty()) {
                                    viewModel.clearSelection()
                                } else {
                                    onBackClick()
                                }
                            }) {
                                Icon(
                                    imageVector = if (uiState.selectedMessageIds.isNotEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            if (uiState.selectedMessageIds.isNotEmpty()) {
                                IconButton(onClick = {
                                    val textToCopy = viewModel.getSelectedMessagesText()
                                    if (textToCopy.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(textToCopy))
                                        val count = uiState.selectedMessageIds.size
                                        val feedbackText = if (count == 1) "Message copied" else "$count messages copied"
                                        feedbackHandler(TransientFeedback.Copy(feedbackText))
                                    }
                                    viewModel.clearSelection()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Selected")
                                }
                                IconButton(onClick = {
                                    viewModel.deleteSelectedMessages(feedbackHandler)
                                }) {
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
                                            showDeleteConfirmation = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                    )
                                    HorizontalDivider()
                                    uiState.savingsSpaces.forEach { space ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(space.icon)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(space.name)
                                                }
                                            },
                                            onClick = {
                                                viewModel.setActiveSpace(space.id)
                                                showMenu = false
                                            },
                                            trailingIcon = {
                                                if (uiState.activeSpaceId == space.id) {
                                                    RadioButton(selected = true, onClick = null)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (uiState.selectedMessageIds.isNotEmpty())
                                MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    // Active Space Indicator
                    uiState.savingsSpaces.find { it.id == uiState.activeSpaceId }?.let { activeSpace ->
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.sir_quack),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Saving to: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${activeSpace.icon} ${activeSpace.name}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            },
            bottomBar = {
                Column {
                    val feedback = LocalTransientFeedback.current
                    val actionHandler = LocalTransientFeedbackActionHandler.current

                    AnimatedVisibility(
                        visible = feedback is TransientFeedback.UndoDelete,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        if (feedback is TransientFeedback.UndoDelete) {
                            com.example.tasama.presentation.components.UndoDeleteBanner(
                                text = feedback.text,
                                onUndo = { actionHandler(feedback) }
                            )
                        }
                    }

                    CorrectionPrompt(
                        pendingCorrection = uiState.pendingCorrection,
                        onConfirm = viewModel::confirmCorrection,
                        onCancel = viewModel::cancelCorrection
                    )
                    SpaceTransactionPrompt(
                        pending = uiState.pendingSpaceTransaction,
                        onConfirm = viewModel::confirmSpaceTransaction,
                        onCancel = viewModel::cancelSpaceTransaction
                    )
                    AIInput(
                        message = uiState.inputText,
                        onMessageChange = viewModel::onInputChange,
                        onSend = viewModel::sendMessage,
                        isSending = uiState.isTyping
                    )
                }
            },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                AIContent(
                    uiState = uiState,
                    onLoadMore = viewModel::loadMoreMessages,
                    onMessageLongClick = viewModel::toggleMessageSelection,
                    onMessageClick = { messageId ->
                        if (uiState.selectedMessageIds.isNotEmpty()) {
                            viewModel.toggleMessageSelection(messageId)
                        }
                    },
                    onUndoClick = viewModel::undoTransaction,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Hapus Chat History?") },
            text = { Text("Semua pesan dengan Sir Quack akan dihapus. Riwayat transaksi tabungan Anda tidak akan terpengaruh.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun CorrectionPrompt(
    pendingCorrection: PendingCorrection?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = pendingCorrection != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (pendingCorrection != null) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val newTx = pendingCorrection.newTransaction
                    val oldTx = pendingCorrection.originalTransaction
                    
                    Text(
                        text = "Konfirmasi Perubahan",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Old values
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = oldTx.amount.formatCurrency(oldTx.currency),
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                text = oldTx.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                        }
                        
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        // New values
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = newTx.amount.formatCurrency(newTx.currency),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = newTx.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = pendingCorrection.confirmationText,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Konfirmasi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpaceTransactionPrompt(
    pending: PendingSpaceTransaction?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = pending != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (pending != null) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pindah Space & Simpan?",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    pending.transactions.forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val type = if (tx.type.name == "INCOME") "Nabung" else "Pengeluaran"
                            Text(
                                text = "${tx.note.ifEmpty { type }} (${pending.spaceName})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = tx.amount.formatCurrency(tx.currency),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (tx.type == com.example.tasama.domain.model.TransactionType.INCOME)
                                    Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Target Space: ${pending.spaceName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Catat ke ${pending.spaceName}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean = false,
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
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 1.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = message,
                        onValueChange = onMessageChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tanya Sir Quack...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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

@Composable
fun AIContent(
    uiState: AIUiState,
    onLoadMore: () -> Unit,
    onMessageLongClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    onUndoClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val reversedMessages = remember(uiState.messages) {
        uiState.messages.asReversed()
    }

    // Detect when user scrolls to the "top" (which is now the end of the list in reverseLayout)
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

    // Auto-scroll to bottom when keyboard opens
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && reversedMessages.isNotEmpty() && listState.firstVisibleItemIndex < 2) {
            listState.animateScrollToItem(0)
        }
    }

    // Auto-scroll to bottom when new messages arrive or AI starts typing
    LaunchedEffect(reversedMessages.firstOrNull()?.id, uiState.isTyping) {
        if (reversedMessages.isNotEmpty() || uiState.isTyping) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMoreMessages && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            // In reverseLayout, index 0 is the bottom message.
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 10
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
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

            items(reversedMessages.size, key = { reversedMessages[it].id }) { index ->
                val message = reversedMessages[index]
                val date = Instant
                    .fromEpochMilliseconds(message.timestamp)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date

                // In reverseLayout, index 0 is bottom.
                // Header shows if it's the last message in the reversed list (top of chat history)
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
                    MessageBubble(
                        message = message,
                        isSelected = isSelected,
                        undoableTransaction = uiState.undoableTransaction,
                        onUndoClick = onUndoClick,
                        onLongClick = { onMessageLongClick(message.id) },
                        onClick = { onMessageClick(message.id) }
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
    isSelected: Boolean = false,
    undoableTransaction: UndoableTransaction? = null,
    onUndoClick: (String, String) -> Unit = { _, _ -> },
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val isUser = message.isFromMe
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val isUndoable = !isUser && undoableTransaction?.messageId == message.id

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
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
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            shadowElevation = 0.5.dp,
            modifier = Modifier.widthIn(max = 280.dp)
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
                    }

                    if (isUndoable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onUndoClick(undoableTransaction.spaceId, undoableTransaction.transactionId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Batal",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
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
            onLoadMore = {},
            onMessageLongClick = {},
            onMessageClick = {},
            onUndoClick = { _, _ -> }
        )
    }
}