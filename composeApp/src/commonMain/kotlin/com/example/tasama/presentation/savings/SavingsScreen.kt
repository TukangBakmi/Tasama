package com.example.tasama.presentation.savings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
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
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.SavingsSpaceType
import com.example.tasama.domain.model.TransactionType
import androidx.compose.ui.tooling.preview.Preview
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
        SavingsContent(
            uiState = uiState,
            onInviteClick = { viewModel.onInviteClick(it) },
            onAddTransactionClick = { viewModel.onAddTransactionClick(it) },
            modifier = Modifier.padding(paddingValues)
        )

        if (uiState.showAddSpaceDialog) {
            AddSpaceDialog(
                onDismiss = { viewModel.onDismissAddSpace() },
                onConfirm = { name, target, icon, type ->
                    viewModel.addSpace(
                        SavingsSpace(
                            name = name,
                            targetAmount = target,
                            icon = icon,
                            type = type
                        )
                    )
                    viewModel.onDismissAddSpace()
                }
            )
        }

        if (uiState.showInviteMemberDialog) {
            InviteMemberDialog(
                onDismiss = { viewModel.onDismissInvite() },
                onConfirm = { email ->
                    viewModel.inviteMember(email)
                }
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
    onInviteClick: (String) -> Unit,
    onAddTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.savingsSpaces.isEmpty() && !uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No savings spaces yet. Create one!", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.savingsSpaces) { space ->
                SavingsSpaceItem(
                    space = space,
                    onInviteClick = { onInviteClick(space.id) },
                    onAddTransactionClick = { onAddTransactionClick(space.id) }
                )
            }
        }
    }
}

@Composable
fun SavingsSpaceItem(
    space: SavingsSpace,
    onInviteClick: () -> Unit,
    onAddTransactionClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
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
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(space.icon, fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = space.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val typeLabel = when (space.type) {
                                SavingsSpaceType.PERSONAL -> "Personal"
                                SavingsSpaceType.COUPLE -> "Couple"
                                SavingsSpaceType.GROUP -> "Group"
                            }
                            if (space.type != SavingsSpaceType.PERSONAL) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Row {
                    IconButton(onClick = onAddTransactionClick) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Transaction",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (space.type != SavingsSpaceType.PERSONAL) {
                        IconButton(onClick = onInviteClick) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = "Invite Member",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (space.targetAmount != null) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatCurrency(space.balance),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (space.targetAmount != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Target",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatCurrency(space.targetAmount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            if (expanded) {
                if (space.members.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Members",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        space.members.forEachIndexed { index, member ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .offset(x = (index * -8).dp)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    member.name.take(1),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                
                if (space.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Description",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        space.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AddSpaceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, String, SavingsSpaceType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("💰") }
    var type by remember { mutableStateOf(SavingsSpaceType.PERSONAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Savings Space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Space Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target Amount (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (Emoji)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Space Type", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SavingsSpaceType.entries.forEach { spaceType ->
                        FilterChip(
                            selected = type == spaceType,
                            onClick = { type = spaceType },
                            label = { Text(spaceType.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, target.toDoubleOrNull(), icon, type) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun InviteMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Member") },
        text = {
            Column {
                Text("Enter the email address of the person you want to invite.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(email) }, enabled = email.isNotBlank()) {
                Text("Invite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
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
        title = { Text("Add Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransactionType.entries.forEach { transType ->
                        FilterChip(
                            selected = type == transType,
                            onClick = { type = transType },
                            label = { Text(transType.name) }
                        )
                    }
                }
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, type, note) },
                enabled = amount.isNotBlank()
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

private fun formatCurrency(amount: Double): String {
    val parts = amount.toLong().toString().reversed().chunked(3)
    return "Rp " + parts.joinToString(".").reversed()
}

@Preview
@Composable
fun SavingsPreview() {
    MaterialTheme {
        Surface {
            SavingsContent(
                uiState = SavingsUiState(
                    savingsSpaces = listOf(
                        SavingsSpace(
                            id = "1",
                            name = "New Car",
                            targetAmount = 50000000.0,
                            balance = 15000000.0,
                            icon = "🚗",
                            type = SavingsSpaceType.COUPLE
                        ),
                        SavingsSpace(
                            id = "2",
                            name = "Japan Trip",
                            targetAmount = 30000000.0,
                            balance = 25000000.0,
                            icon = "🗾",
                            type = SavingsSpaceType.PERSONAL
                        )
                    )
                ),
                onInviteClick = {},
                onAddTransactionClick = {}
            )
        }
    }
}
