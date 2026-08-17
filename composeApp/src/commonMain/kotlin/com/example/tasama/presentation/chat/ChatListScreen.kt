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
import androidx.compose.material.icons.filled.*
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
import com.example.tasama.presentation.components.UserAvatar
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
                if (uiState.isSelectionMode) {
                    TopAppBar(
                        title = { Text("${uiState.selectedChannelIds.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.toggleSelectionMode(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.showDeleteConfirmation(true) },
                                enabled = uiState.selectedChannelIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                } else {
                    TopAppBar(
                        title = { Text("Messages", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(onClick = { showAddContactDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        val displayItems = remember(uiState.channels, uiState.channelUsers) {
            val currentUid = viewModel.currentUserId
            uiState.channels.map { channel ->
                val otherId = channel.participantIds.find { it != currentUid }
                Triple(otherId?.let { uiState.channelUsers[it] }, channel, channel.id)
            }
        }

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // AI Advisor at the very top
                    item {
                        var showMenu by remember { mutableStateOf(false) }
                        var showDeleteConfirmation by remember { mutableStateOf(false) }

                        Box {
                            AIAdvisorItem(
                                onClick = onAIClick,
                                onLongClick = { if (!uiState.isSelectionMode) showMenu = true }
                            )
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
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }

                        if (showDeleteConfirmation) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirmation = false },
                                title = { Text("Hapus Chat Sir Quack?") },
                                text = { Text("Semua riwayat percakapan dengan Sir Quack akan dihapus. Riwayat transaksi tabungan Anda akan tetap aman.") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.clearAIChatHistory()
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

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    if (displayItems.isEmpty()) {
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
                        items(displayItems, key = { it.third }) { (user, channel, _) ->
                            var showMenu by remember { mutableStateOf(false) }
                            val isSelected = uiState.selectedChannelIds.contains(channel.id)

                            Box {
                                ChannelItem(
                                    channel = channel,
                                    currentUserId = viewModel.currentUserId,
                                    otherUser = user,
                                    now = now,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleChannelSelection(channel.id)
                                        } else {
                                            onChannelClick(channel.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!uiState.isSelectionMode) {
                                            viewModel.toggleSelectionMode(true)
                                            viewModel.toggleChannelSelection(channel.id)
                                        }
                                    }
                                )
                                if (!uiState.isSelectionMode) {
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
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                    }
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
                viewModel.createChannel(userId) { channelId ->
                    if (channelId != null) {
                        onChannelClick(channelId)
                        showAddContactDialog = false
                    }
                }
            },
            onDeleteContact = { user ->
                viewModel.setContactToDelete(user)
            }
        )
    }

    uiState.contactToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { viewModel.setContactToDelete(null) },
            title = { Text("Remove this contact?") },
            text = { Text("Are you sure you want to remove ${user.name} from your contacts? Chat history will remain accessible.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.removeContact(user.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setContactToDelete(null) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirmation(false) },
            title = { Text("Delete selected chats?") },
            text = { Text("Delete ${uiState.selectedChannelIds.size} conversations? This will remove them from your list but keep them for other participants.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSelectedChannels() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirmation(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AIAdvisorItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
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
    channel: ChatChannel?, 
    currentUserId: String?, 
    otherUser: User?,
    now: Long,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .pointerInput(isSelectionMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Box(
            modifier = Modifier.size(48.dp)
        ) {
            UserAvatar(
                user = otherUser,
                modifier = Modifier.fillMaxSize(),
                fallbackName = otherUser?.name ?: channel?.participantNames?.filterKeys { it != currentUserId }?.values?.firstOrNull()
            )
            
            // Online status indicator
            val isOnline = remember(otherUser?.lastActive, now) {
                val lastActive = otherUser?.lastActive ?: 0L
                lastActive != 0L && (now - lastActive < 30000)
            }
            
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF4CAF50) else Color.Gray)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayName = otherUser?.name ?: run {
                    val otherParticipants = channel?.participantNames?.filterKeys { it != currentUserId } ?: emptyMap()
                    if (otherParticipants.isNotEmpty()) {
                        otherParticipants.values.joinToString(", ")
                    } else {
                        channel?.participantNames?.values?.joinToString(", ") ?: "Unknown"
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
                
                val timeString = remember(channel?.lastMessageTimestamp) {
                    val timestamp = channel?.lastMessageTimestamp ?: 0L
                    if (timestamp == 0L) return@remember ""
                    try {
                        val instant = Instant.fromEpochMilliseconds(timestamp)
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
                if (channel?.lastMessageSenderId == currentUserId && channel?.lastMessageSenderId?.isNotEmpty() == true) {
                    val otherId = channel.participantIds.find { it != currentUserId }
                    if (otherId != null) {
                        val isRead = channel.lastMessageReadBy.containsKey(otherId)
                        val isDelivered = channel.lastMessageDeliveredTo.containsKey(otherId)
                        MessageStatusIcon(
                            isRead = isRead,
                            isDelivered = isDelivered,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Text(
                    text = channel?.lastMessage ?: "No messages yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val unreadCount = currentUserId?.let { channel?.unreadCounts?.get(it) } ?: 0
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
    onAdd: (String) -> Unit,
    onDeleteContact: (User) -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Enter the 12-digit User ID or UID of the person you want to chat with.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = query,
                        onValueChange = {
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
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth().clickable { onAdd(user.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                user = User(id = user.id, name = user.name, avatarUrl = user.avatarUrl),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(user.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (uiState.contacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Saved Contacts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(uiState.contacts) { contact ->
                            // Use latest data from channelUsers if available for real-time status
                            val updatedContact = uiState.channelUsers[contact.id] ?: contact
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(contact.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    user = updatedContact,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = updatedContact.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDeleteContact(updatedContact) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove contact",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
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
