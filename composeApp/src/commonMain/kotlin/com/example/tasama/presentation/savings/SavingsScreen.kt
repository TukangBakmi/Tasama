package com.example.tasama.presentation.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.tasama.util.formatCurrency
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.*
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    viewModel: SavingsViewModel = koinViewModel(),
    onNavigateToDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = com.example.tasama.presentation.main.LocalSnackbarHostState.current

    LaunchedEffect(uiState.selectedSpaceId) {
        if (uiState.selectedSpaceId != null) {
            onNavigateToDetail(uiState.selectedSpaceId!!)
            viewModel.onSpaceHandled()
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

    LaunchedEffect(Unit) {
        snapshotFlow { uiState.successMessage }
            .filterNotNull()
            .collect { message ->
                viewModel.clearSuccessMessage()
                snackbarHostState.showSnackbar(message)
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    userCurrency = uiState.userCurrency,
                    onDismiss = { viewModel.onDismissAddSpace() },
                    onConfirm = { name, target, icon, type, desc, currency ->
                        viewModel.addSpace(
                            SavingsSpace(
                                name = name,
                                targetAmount = target,
                                icon = icon,
                                type = type,
                                description = desc,
                                currency = currency
                            )
                        )
                        viewModel.onDismissAddSpace()
                    }
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
                val space = uiState.selectedSpace
                if (space != null) {
                    AddTransactionDialog(
                        currency = space.currency,
                        onDismiss = { viewModel.onDismissAddTransaction() },
                        onConfirm = { amount, type, note ->
                            viewModel.addTransaction(amount, type, note)
                        }
                    )
                }
            }

            if (uiState.showRemovedFromSpaceDialog) {
                RemovedFromSpaceDialog(
                    onConfirm = {
                        viewModel.onRemovedDialogConfirm()
                        onNavigateToDetail("") // This is a bit hacky, but we need to trigger the navigation back
                    }
                )
            }
        }
    }
}

@Composable
fun RemovedFromSpaceDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { }, // Force confirmation
        title = { Text("Removed from Savings Space") },
        text = { Text("You no longer have access to this Savings Space.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("OK")
            }
        }
    )
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
                Text("📩", fontSize = 20.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(invitation.spaceName, fontWeight = FontWeight.Bold)
                Text("Invited by ${invitation.inviterName}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFF2E7D32))
            }
            IconButton(onClick = onDecline) {
                Icon(Icons.Default.Close, contentDescription = "Decline", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SavingsSpaceItem(
    space: SavingsSpace,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(space.icon, fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(space.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            space.balance.formatCurrency(space.currency),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
            }

            if (space.targetAmount != null && space.targetAmount > 0) {
                val progress = (space.balance.toDouble() / space.targetAmount).toFloat().coerceIn(0f, 1f)
                Spacer(Modifier.height(20.dp))
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${(progress * 100).toInt()}% of ${space.targetAmount.formatCurrency(space.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        if (progress >= 1f) {
                            Text("Goal Reached! 🎉", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailsScreen(
    uiState: SavingsUiState,
    isOwner: Boolean,
    currentUserId: String,
    onBack: () -> Unit,
    onAddTransaction: (String) -> Unit,
    onConfirmAddTransaction: (Long, TransactionType, String) -> Unit,
    onDismissAddTransaction: () -> Unit,
    onInvite: (String) -> Unit,
    onEdit: (SavingsSpace) -> Unit,
    onDelete: (String) -> Unit,
    onLeave: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onTransferOwnership: (String) -> Unit,
    onCancelInvitation: (String) -> Unit,
    onDeleteTransaction: (SavingsTransaction) -> Unit
) {
    val space = uiState.selectedSpace ?: return
    var tabIndex by remember { mutableStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteTransactionConfirm by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<SavingsTransaction?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = space.targetDate
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(space.icon)
                        Spacer(Modifier.width(8.dp))
                        Text(space.name, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Set Target Date")
                        }
                    }
                }
            )
        },
        bottomBar = {
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
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
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
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                val isPersonal = space.type == SavingsSpaceType.PERSONAL
                SecondaryTabRow(selectedTabIndex = tabIndex, containerColor = Color.Transparent) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Overview") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("History") })
                    if (!isPersonal) {
                        Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("Members") })
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (tabIndex) {
                        0 -> OverviewTab(
                            space = space,
                            transactions = uiState.transactions,
                            onDeleteTransaction = { tx ->
                                transactionToDelete = tx
                                showDeleteTransactionConfirm = true
                            }
                        )
                        1 -> HistoryTab(uiState.activityHistory)
                        2 -> {
                            if (!isPersonal) {
                                MembersTab(
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
                    }
                }
            }

            if (uiState.showAddTransactionDialog) {
                AddTransactionDialog(
                    currency = space.currency,
                    onDismiss = onDismissAddTransaction,
                    onConfirm = onConfirmAddTransaction
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onEdit(space.copy(targetDate = it))
                    }
                    showDatePicker = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEditDialog) {
        AddSpaceDialog(
            initialSpace = space,
            userCurrency = space.currency,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, target, icon, type, desc, currency ->
                onEdit(space.copy(name = name, targetAmount = target, icon = icon, type = type, description = desc, currency = currency))
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        val isGroup = space.type != SavingsSpaceType.PERSONAL
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Savings Space?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This action will permanently delete this Savings Space and all its contribution history.")
                    if (isGroup) {
                        Text(
                            "Warning: This will also remove access and history for all other members.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onDelete(space.id); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
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
            text = { Text("You will no longer be able to see or contribute to this space.") },
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

    if (showDeleteTransactionConfirm && transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteTransactionConfirm = false
                transactionToDelete = null
            },
            title = { Text("Delete Contribution?") },
            text = {
                Column {
                    Text("Are you sure you want to delete this contribution of ${transactionToDelete!!.amount.formatCurrency(transactionToDelete!!.currency)}?")
                    if (transactionToDelete!!.note.isNotBlank()) {
                        Text("Note: ${transactionToDelete!!.note}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("This will reduce the space balance.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTransaction(transactionToDelete!!)
                        showDeleteTransactionConfirm = false
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteTransactionConfirm = false
                    transactionToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun OverviewTab(
    space: SavingsSpace,
    transactions: List<SavingsTransaction>,
    onDeleteTransaction: (SavingsTransaction) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            if (space.targetAmount != null && space.targetAmount > 0) {
                val progress = (space.balance.toDouble() / space.targetAmount).toFloat().coerceIn(0f, 1f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Goal Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        if (space.targetDate != null) {
                            val now = Clock.System.now().toEpochMilliseconds()
                            val remaining: Long = space.targetDate - now
                            val daysRemaining: Long = if (remaining > 0L) remaining / (1000L * 60 * 60 * 24) else 0L
                            
                            Surface(
                                color = if (daysRemaining < 7) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (daysRemaining > 0) "$daysRemaining days left" else "Deadline reached",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (daysRemaining < 7) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(space.balance.formatCurrency(space.currency), fontWeight = FontWeight.Bold)
                        Text(space.targetAmount.formatCurrency(space.currency), color = Color.Gray)
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
                TransactionListItem(tx, onDelete = { onDeleteTransaction(tx) })
            }
        }
    }
}

@Composable
fun HistoryTab(activities: List<SavingsActivity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(activities) { activity ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (activity.type) {
                        SavingsActivityType.TRANSACTION_ADDED -> "💰"
                        SavingsActivityType.MEMBER_JOINED -> "👤"
                        SavingsActivityType.SPACE_CREATED -> "✨"
                        else -> "📝"
                    }
                    Text(icon, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(activity.details, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${activity.userName} • ${formatTimestamp(activity.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (isOwner) {
            item {
                Button(
                    onClick = onInvite,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Invite Member")
                }
            }
        }

        if (pendingInvitations.isNotEmpty()) {
            item {
                Text("Pending Invitations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(pendingInvitations) { inv ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(inv.inviteeName.ifBlank { "User ${inv.inviteeId.takeLast(4)}" }, fontWeight = FontWeight.Medium)
                        Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (isOwner) {
                        TextButton(onClick = { onCancelInvitation(inv.id) }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            Text("Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(space.members) { member ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        user = User(name = member.name, avatarUrl = member.avatarUrl),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(member.name, fontWeight = FontWeight.SemiBold)
                        Text(member.role.name, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                
                if (isOwner && member.userId != currentUserId) {
                    var showMemberOptions by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMemberOptions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMemberOptions,
                            onDismissRequest = { showMemberOptions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Transfer Ownership") },
                                onClick = {
                                    onTransfer(member.userId)
                                    showMemberOptions = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove Member", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    onRemove(member.userId)
                                    showMemberOptions = false
                                }
                            )
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
                "${if (tx.type == TransactionType.INCOME) "" else "-"} ${tx.amount.formatCurrency(tx.currency)}",
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
    userCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, String, SavingsSpaceType, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialSpace?.name ?: "") }
    var description by remember { mutableStateOf(initialSpace?.description ?: "") }
    var targetText by remember { mutableStateOf(if (initialSpace?.targetAmount != null) formatNumericInput(initialSpace.targetAmount.toString()) else "") }
    var icon by remember { mutableStateOf(initialSpace?.icon ?: "💰") }
    var type by remember { mutableStateOf(initialSpace?.type ?: SavingsSpaceType.PERSONAL) }
    var currency by remember { mutableStateOf(initialSpace?.currency ?: userCurrency) }
    
    val icons = listOf("💰", "🏠", "🚗", "✈️", "🎓", "💍", "🏖️", "🎁", "📱", "💻", "☕", "🎮")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSpace == null) "Create Savings Space" else "Edit Savings Space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose Icon", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    icons.take(6).forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (icon == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(1.dp, if (icon == emoji) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f), CircleShape)
                                .clickable { icon = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    icons.drop(6).forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (icon == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(1.dp, if (icon == emoji) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f), CircleShape)
                                .clickable { icon = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = formatNumericInput(it) },
                    label = { Text("Target Amount (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                
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
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, parseNumericInput(targetText), icon, type, description, currency)
                    }
                }
            ) { Text(if (initialSpace == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onQueryChange,
                    label = { Text("Search by name or ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (uiState.isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                if (uiState.searchedUser != null && !uiState.filteredContacts.any { it.id == uiState.searchedUser.id }) {
                    Text("Found User", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    UserSelectionItem(user = uiState.searchedUser, onSelect = { onInvite(uiState.searchedUser.id) })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                if (uiState.filteredContacts.isNotEmpty()) {
                    Text("Suggested Contacts", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(uiState.filteredContacts) { contact ->
                            UserSelectionItem(user = contact, onSelect = { onInvite(contact.id) })
                        }
                    }
                } else if (!uiState.isSearching && uiState.searchQuery.isNotEmpty() && uiState.searchedUser == null) {
                    Text("No users found", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(8.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun UserSelectionItem(user: User, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            user = user,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, fontWeight = FontWeight.Medium)
            Text("ID: ${user.shortId}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AddTransactionDialog(
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, TransactionType, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
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
                    value = amountText,
                    onValueChange = { amountText = formatNumericInput(it) },
                    label = { Text("Amount ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = parseNumericInput(amountText)
                    if (amount != null && amount > 0) {
                        onConfirm(amount, type, note)
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}



private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val timeZone = TimeZone.currentSystemDefault()
    val dateTime = instant.toLocalDateTime(timeZone)
    val now = Clock.System.now().toLocalDateTime(timeZone)
    val yesterday = Clock.System.now().minus(24, DateTimeUnit.HOUR).toLocalDateTime(timeZone)

    val dateStr = when {
        dateTime.date == now.date -> "Today"
        dateTime.date == yesterday.date -> "Yesterday"
        else -> "${dateTime.day.toString().padStart(2, '0')}/${dateTime.month.number.toString().padStart(2, '0')}/${dateTime.year}"
    }

    val timeStr = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    return "$dateStr $timeStr"
}

private fun formatNumericInput(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return digits.reversed().chunked(3).joinToString(".").reversed()
}

private fun parseNumericInput(input: String): Long? {
    return input.filter { it.isDigit() }.toLongOrNull()
}
