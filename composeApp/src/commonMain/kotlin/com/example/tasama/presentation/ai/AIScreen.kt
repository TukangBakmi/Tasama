package com.example.tasama.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import tasama.composeapp.generated.resources.Res
import tasama.composeapp.generated.resources.sir_quack
import com.example.tasama.presentation.main.LocalSnackbarHostState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIScreen(
    viewModel: AIViewModel,
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

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
                            IconButton(onClick = { viewModel.deleteSelectedMessages() }) {
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
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aktif di: ${activeSpace.icon} ${activeSpace.name}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
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
                CorrectionPrompt(
                    pendingCorrection = uiState.pendingCorrection,
                    onConfirm = viewModel::confirmCorrection,
                    onCancel = viewModel::cancelCorrection
                )
                AIInput(
                    message = uiState.inputText,
                    onMessageChange = viewModel::onInputChange,
                    onSend = viewModel::sendMessage
                )
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        AIContent(
            uiState = uiState,
            onLoadMore = viewModel::loadMoreMessages,
            onMessageLongClick = viewModel::toggleMessageSelection,
            onMessageClick = { messageId ->
                if (uiState.selectedMessageIds.isNotEmpty()) {
                    viewModel.toggleMessageSelection(messageId)
                }
            },
            modifier = Modifier.padding(paddingValues)
        )
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
                    Text(
                        text = pendingCorrection.confirmationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
    onMessageLongClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
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
                val date = Instant
                    .fromEpochMilliseconds(message.timestamp)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
                
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
                    val isSelected = uiState.selectedMessageIds.contains(message.id)
                    MessageBubble(
                        message = message,
                        isSelected = isSelected,
                        onLongClick = { onMessageLongClick(message.id) },
                        onClick = { onMessageClick(message.id) }
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val isUser = message.isFromMe
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    
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
        RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp)
    } else {
        RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp)
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
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
            onMessageClick = {}
        )
    }
}