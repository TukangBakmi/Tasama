package com.example.tasama.presentation.savings

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.*
import com.example.tasama.presentation.components.UserAvatar
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    viewModel: SavingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = com.example.tasama.presentation.main.LocalSnackbarHostState.current

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
            TopAppBar(
                title = { Text("Savings Spaces", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.onAddSpaceClick() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Space")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            SavingsContent(
                uiState = uiState,
                onSpaceClick = { viewModel.onSpaceClick(it) },
                onAcceptInvite = { viewModel.acceptInvitation(it) },
                onDeclineInvite = { viewModel.declineInvitation(it) }
            )
        }

        if (uiState.showAddSpaceDialog) {
            AddSpaceDialog(
                onDismiss = { viewModel.onDismissAddSpace() },
                onConfirm = { name, target, icon, type, desc ->
                    viewModel.addSpace(
                        SavingsSpace(
                            name = name,
                            targetAmount = target,
                            icon = icon,
                            type = type,
                            description = desc
                        )
                    )
                    viewModel.onDismissAddSpace()
                }
            )
        }

        if (uiState.showSpaceDetails) {
            SpaceDetailsDialog(
                uiState = uiState,
                isOwner = viewModel.isOwner(uiState.selectedSpace),
                currentUserId = viewModel.getCurrentUserId() ?: "",
                onDismiss = { viewModel.onDismissSpaceDetails() },
                onAddTransaction = { viewModel.onAddTransactionClick(it) },
                onInvite = { viewModel.onInviteClick(it) },
                onEdit = { viewModel.updateSpace(it) },
                onDelete = { viewModel.deleteSpace(it) },
                onArchive = { viewModel.archiveSpace(it) },
                onLeave = { viewModel.leaveSpace() },
                onRemoveMember = { viewModel.removeMember(it) },
                onTransferOwnership = { viewModel.transferOwnership(it) },
                onCancelInvitation = { viewModel.cancelInvitation(it) },
                onDeleteTransaction = { viewModel.deleteTransaction(it) }
            )
        }

        if (uiState.showInviteMemberDialog) {
            InviteMemberDialog(
                uiState = uiState,
                onDismiss = { viewModel.onDismissInvite() },
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                onInvite = { viewModel.inviteMember(it) }
            )
        }

        if (uiState.showAddTransactionDialog) {
            AddTransactionDialog(
                onDismiss = { viewModel.onDismissAddTransaction() },
                onConfirm = { amount, type, note ->
                    viewModel.addTransaction(amount, type, note)
                }
            )
        }
    }
}

@Composable
fun SavingsContent(
    uiState: SavingsUiState,
    onSpaceClick: (String) -> Unit,
    onAcceptInvite: (String) -> Unit,
    onDeclineInvite: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.myInvitations.isNotEmpty()) {
            item {
                Text(
                    "Pending Invitations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(uiState.myInvitations) { invitation ->
                InvitationItem(
                    invitation = invitation,
                    onAccept = { onAcceptInvite(invitation.id) },
                    onDecline = { onDeclineInvite(invitation.id) }
                )
            }
        }

        item {
            Text(
                "Your Spaces",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (uiState.savingsSpaces.isEmpty() && !uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No savings spaces yet. Create one!", color = Color.Gray)
                }
            }
        } else {
            items(uiState.savingsSpaces) { space ->
                SavingsSpaceItem(
                    space = space,
                    onClick = { onSpaceClick(space.id) }
                )
            }
        }
    }
}

@Composable
fun InvitationItem(
    invitation: SavingsInvitation,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(invitation.spaceName, fontWeight = FontWeight.Bold)
                Text("Invited by ${invitation.inviterName}", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = onDecline) {
                    Icon(Icons.Default.Close, contentDescription = "Decline", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onAccept) {
                    Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFF2E7D32))
                }
            }
        }
    }
}

@Composable
fun SavingsSpaceItem(
    space: SavingsSpace,
    onClick: () -> Unit
) {
    val progress = space.targetAmount?.let { target ->
        if (target > 0) (space.balance / target).toFloat().coerceIn(0f, 1f) else 0f
    } ?: 0f
    
    val animatedProgress by animateFloatAsState(targetValue = progress)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(space.icon, fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(space.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            when (space.type) {
                                SavingsSpaceType.PERSONAL -> "Personal"
                                SavingsSpaceType.COUPLE -> "Couple"
                                SavingsSpaceType.GROUP -> "Group"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    formatCurrency(space.balance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (space.targetAmount != null) {
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${(progress * 100).toInt()}% of ${formatCurrency(space.targetAmount)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailsDialog(
    uiState: SavingsUiState,
    isOwner: Boolean,
    currentUserId: String,
    onDismiss: () -> Unit,
    onAddTransaction: (String) -> Unit,
    onInvite: (String) -> Unit,
    onEdit: (SavingsSpace) -> Unit,
    onDelete: (String) -> Unit,
    onArchive: (String) -> Unit,
    onLeave: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onTransferOwnership: (String) -> Unit,
    onCancelInvitation: (String) -> Unit,
    onDeleteTransaction: (String) -> Unit
) {
    val space = uiState.selectedSpace ?: return
    var tabIndex by remember { mutableStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(space.icon, fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(space.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(formatCurrency(space.balance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (isOwner) {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            }

            TabRow(selectedTabIndex = tabIndex, containerColor = Color.Transparent) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Overview") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("History") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("Members") })
            }

            Box(modifier = Modifier.weight(1f)) {
                when (tabIndex) {
                    0 -> OverviewTab(space, uiState.transactions, onAddTransaction, onDeleteTransaction)
                    1 -> HistoryTab(uiState.activityHistory)
                    2 -> MembersTab(
                        space = space,
                        isOwner = isOwner,
                        currentUserId = currentUserId,
                        pendingInvitations = uiState.pendingInvitations,
                        onInvite = { onInvite(space.id) },
                        onRemove = onRemoveMember,
                        onTransfer = onTransferOwnership,
                        onCancelInvitation = onCancelInvitation
                    )
                }
            }

            // Actions Footer
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isOwner) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showLeaveConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Leave")
                        }
                    }
                    
                    Button(
                        onClick = { onAddTransaction(space.id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Savings")
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AddSpaceDialog(
            initialSpace = space,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, target, icon, type, desc ->
                onEdit(space.copy(name = name, targetAmount = target, icon = icon, type = type, description = desc))
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Savings Space?") },
            text = { Text("This action cannot be undone. All transactions and member associations will be removed.") },
            confirmButton = {
                Button(onClick = { onDelete(space.id); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Savings Space?") },
            text = { Text("You will no longer be able to view or add transactions to this space.") },
            confirmButton = {
                Button(onClick = { onLeave(); showLeaveConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun OverviewTab(
    space: SavingsSpace,
    transactions: List<SavingsTransaction>,
    onAddTransaction: (String) -> Unit,
    onDeleteTransaction: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            if (space.targetAmount != null) {
                val progress = (space.balance / space.targetAmount).toFloat().coerceIn(0f, 1f)
                Column {
                    Text("Goal Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatCurrency(space.balance), fontWeight = FontWeight.Bold)
                        Text(formatCurrency(space.targetAmount), color = Color.Gray)
                    }
                }
            }
        }

        if (space.description.isNotEmpty()) {
            item {
                Column {
                    Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(space.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Text("Recent Contributions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (transactions.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions yet", color = Color.Gray)
                }
            }
        } else {
            items(transactions.take(10)) { tx ->
                TransactionListItem(tx, onDelete = { onDeleteTransaction(tx.id) })
            }
        }
    }
}

@Composable
fun HistoryTab(activities: List<SavingsActivity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (activities.isEmpty()) {
            item {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No activity history yet", color = Color.Gray)
                }
            }
        } else {
            items(activities) { activity ->
                Row(verticalAlignment = Alignment.Top) {
                    val icon = when (activity.type) {
                        SavingsActivityType.MEMBER_JOINED -> Icons.Default.PersonAdd
                        SavingsActivityType.MEMBER_LEFT -> Icons.Default.ExitToApp
                        SavingsActivityType.TRANSACTION_ADDED -> Icons.Default.Add
                        SavingsActivityType.SPACE_CREATED -> Icons.Default.Star
                        else -> Icons.Default.Info
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(activity.details, style = MaterialTheme.typography.bodyMedium)
                        Text(formatTimestamp(activity.timestamp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun MembersTab(
    space: SavingsSpace,
    isOwner: Boolean,
    currentUserId: String,
    pendingInvitations: List<SavingsInvitation>,
    onInvite: () -> Unit,
    onRemove: (String) -> Unit,
    onTransfer: (String) -> Unit,
    onCancelInvitation: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Members (${space.members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isOwner && space.type != SavingsSpaceType.PERSONAL) {
                    TextButton(onClick = onInvite) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Invite")
                    }
                }
            }
        }

        items(space.members) { member ->
            val isMe = member.userId == currentUserId
            val memberIsOwner = member.role == MemberRole.OWNER
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        user = User(id = member.userId, name = member.name, avatarUrl = member.avatarUrl),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(member.name + (if (isMe) " (You)" else ""), fontWeight = FontWeight.Bold)
                        Text(if (memberIsOwner) "Owner" else "Member", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                if (isOwner && !isMe) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Member options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Transfer Ownership") },
                                onClick = { onTransfer(member.userId); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove Member", color = MaterialTheme.colorScheme.error) },
                                onClick = { onRemove(member.userId); menuExpanded = false },
                                leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }
        }

        if (pendingInvitations.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Pending Invitations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(pendingInvitations) { invitation ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(Color.Gray.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            // In a real app we'd fetch the name of the invitee
                            Text("Pending User", color = Color.Gray)
                            Text("Waiting for response", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                    if (isOwner) {
                        IconButton(onClick = { onCancelInvitation(invitation.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionListItem(tx: SavingsTransaction, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                Text(if (tx.type == TransactionType.INCOME) "+" else "-", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tx.note.ifBlank { "Contribution" }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("by ${tx.userName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${if (tx.type == TransactionType.INCOME) "" else "-"} ${formatCurrency(tx.amount)}",
                fontWeight = FontWeight.Bold,
                color = if (tx.type == TransactionType.INCOME) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun AddSpaceDialog(
    initialSpace: SavingsSpace? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, String, SavingsSpaceType, String) -> Unit
) {
    var name by remember { mutableStateOf(initialSpace?.name ?: "") }
    var description by remember { mutableStateOf(initialSpace?.description ?: "") }
    var target by remember { mutableStateOf(initialSpace?.targetAmount?.toString() ?: "") }
    var icon by remember { mutableStateOf(initialSpace?.icon ?: "💰") }
    var type by remember { mutableStateOf(initialSpace?.type ?: SavingsSpaceType.PERSONAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSpace == null) "Create Savings Space" else "Edit Savings Space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Space Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target Amount (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedTextField(value = icon, onValueChange = { icon = it }, label = { Text("Icon (Emoji)") }, modifier = Modifier.fillMaxWidth())
                
                if (initialSpace == null) {
                    Text("Space Type", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SavingsSpaceType.entries.forEach { spaceType ->
                            FilterChip(selected = type == spaceType, onClick = { type = spaceType }, label = { Text(spaceType.name) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, target.toDoubleOrNull(), icon, type, description) }, enabled = name.isNotBlank()) {
                Text(if (initialSpace == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun InviteMemberDialog(
    uiState: SavingsUiState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onInvite: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Member") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Search your contacts by name or enter a 12-digit User ID.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = onQueryChange,
                    label = { Text("Name or User ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, modifier = Modifier.size(18.dp), contentDescription = null) }
                        }
                    }
                )

                if (uiState.searchedUser != null) {
                    val user = uiState.searchedUser
                    Card(
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth().clickable { onInvite(user.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(user = user, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(user.name, fontWeight = FontWeight.Bold)
                                Text("Click to invite", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (uiState.filteredContacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Contacts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                        items(uiState.filteredContacts) { contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onInvite(contact.id) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(user = contact, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(contact.name, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, TransactionType, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.INCOME) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Savings Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { transType ->
                        FilterChip(selected = type == transType, onClick = { type = transType }, label = { Text(transType.name) })
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, type, note) }, enabled = amount.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatCurrency(amount: Double): String {
    val parts = amount.toLong().toString().reversed().chunked(3)
    return "Rp " + parts.joinToString(".").reversed()
}

private fun formatTimestamp(timestamp: Long): String {
    // Simplified timestamp formatting
    return "Just now" // In a real app, use a proper date formatter
}
