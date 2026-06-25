package com.example.tasama.presentation.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.User
import kotlinx.datetime.*
import kotlinx.datetime.number
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import tasama.composeapp.generated.resources.Res
import tasama.composeapp.generated.resources.sir_quack
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChannelClick: (String) -> Unit,
    onAIClick: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = com.example.tasama.presentation.main.LocalSnackbarHostState.current
    var showAddContactDialog by remember { mutableStateOf(false) }
    
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000) // Update every 30 seconds
            now = Clock.System.now().toEpochMilliseconds()
        }
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
                    title = { Text("Messages", fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddContactDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // AI Advisor at the very top
                    item {
                        AIAdvisorItem(onClick = onAIClick)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    if (uiState.channels.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillParentMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("No conversations yet", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Add a contact by ID to start chatting!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(uiState.channels, key = { it.id }) { channel ->
                            var showMenu by remember { mutableStateOf(false) }
                            val otherUserId = channel.participantIds.find { it != viewModel.currentUserId }
                            val otherUser = otherUserId?.let { uiState.channelUsers[it] }

                            Box {
                                ChannelItem(
                                    channel = channel,
                                    currentUserId = viewModel.currentUserId,
                                    otherUser = otherUser,
                                    now = now,
                                    onClick = { onChannelClick(channel.id) },
                                    onLongClick = { showMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Chat") },
                                        onClick = {
                                            viewModel.deleteChannel(channel.id)
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddContactDialog) {
        AddContactDialog(
            uiState = uiState,
            onDismiss = {
                showAddContactDialog = false
                viewModel.clearSearch()
            },
            onSearch = { query -> viewModel.searchUser(query) },
            onAdd = { userId ->
                viewModel.createChannel(userId)
                showAddContactDialog = false
            }
        )
    }
}

@Composable
fun AIAdvisorItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.sir_quack),
                contentDescription = "Sir Quack",
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sir Quack",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Your Personal AI Financial Advisor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChannelItem(
    channel: ChatChannel, 
    currentUserId: String?, 
    otherUser: User?,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            
            // Online status indicator
            val isOnline = remember(otherUser?.lastActive, now) {
                val lastActive = otherUser?.lastActive ?: 0L
                lastActive != 0L && (now - lastActive < 10000)
            }
            
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF4CAF50) else Color.Gray)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayName = otherUser?.name ?: run {
                    val otherParticipants = channel.participantNames.filterKeys { it != currentUserId }
                    if (otherParticipants.isNotEmpty()) {
                        otherParticipants.values.joinToString(", ")
                    } else {
                        channel.participantNames.values.joinToString(", ")
                    }
                }

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                val timeString = remember(channel.lastMessageTimestamp) {
                    try {
                        val instant = Instant.fromEpochMilliseconds(channel.lastMessageTimestamp)
                        val tz = TimeZone.currentSystemDefault()
                        val localDateTime = instant.toLocalDateTime(tz)
                        val now = Clock.System.now().toLocalDateTime(tz)

                        when (localDateTime.date) {
                            now.date -> {
                                val hour = localDateTime.hour.toString().padStart(2, '0')
                                val minute = localDateTime.minute.toString().padStart(2, '0')
                                "$hour:$minute"
                            }
                            now.date.minus(DatePeriod(days = 1)) -> {
                                "Yesterday"
                            }
                            else -> {
                                val day = localDateTime.day.toString().padStart(2, '0')
                                val month = localDateTime.month.number.toString().padStart(2, '0')
                                val year = localDateTime.year.toString().takeLast(2)
                                "$day/$month/$year"
                            }
                        }
                    } catch (_: Exception) { "" }
                }
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val unreadCount = currentUserId?.let { channel.unreadCounts[it] } ?: 0
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddContactDialog(
    uiState: ChatListUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column {
                Text("Enter the 12-digit User ID or UID of the person you want to chat with.")
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = query,
                        onValueChange = {
                            // Only allow numbers and max 12 digits
                            if (it.all { char -> char.isDigit() } && it.length <= 12) {
                                query = it
                            }
                        },
                        label = { Text("User ID") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onSearch(query) },
                        enabled = query.isNotBlank() && !uiState.isSearchingUser
                    ) {
                        if (uiState.isSearchingUser) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }

                uiState.searchedUser?.let { user ->
                    Card(
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(user.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { uiState.searchedUser?.let { onAdd(it.id) } },
                enabled = uiState.searchedUser != null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
