package com.example.tasama.presentation.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.User
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerScreen(
    viewModel: PartnerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = com.example.tasama.presentation.main.LocalSnackbarHostState.current

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            viewModel.clearError()
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partner Tracker") },
                actions = {
                    if (!uiState.isGuest) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isGuest -> GuestPartnerContent(onLogin = viewModel::logout)
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.isLinked -> PartnerMapContent(
                    partner = uiState.partner,
                    anniversaryDate = uiState.currentUser?.anniversaryDate,
                    onEditAnniversary = { showDatePicker = true }
                )
                else -> LinkingContent(
                    uiState = uiState,
                    onShortIdChange = viewModel::onPartnerShortIdChange,
                    onSendRequest = viewModel::sendPartnerRequest,
                    onAcceptRequest = { showDatePicker = true },
                    onDeclineRequest = viewModel::declinePartnerRequest,
                    onCancelRequest = viewModel::cancelPartnerRequest
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
                        if (uiState.isLinked) {
                            viewModel.updateAnniversaryDate(it)
                        } else {
                            viewModel.acceptPartnerRequest(it)
                        }
                    }
                    showDatePicker = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            key(datePickerState.displayMode) {
                DatePicker(
                    state = datePickerState,
                    title = null,
                    headline = {
                        Text(
                            text = "Select anniversary date",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(start = 24.dp)
                                .heightIn(min = 48.dp)
                                .wrapContentHeight(Alignment.CenterVertically)
                        )
                    },
                    showModeToggle = true
                )
            }
        }
    }
}

@Composable
fun GuestPartnerContent(onLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Guest Account",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Partner features are only available for registered users.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Login / Sign Up")
        }
    }
}

@Composable
fun LinkingContent(
    uiState: PartnerUiState,
    onShortIdChange: (String) -> Unit,
    onSendRequest: () -> Unit,
    onAcceptRequest: () -> Unit,
    onDeclineRequest: () -> Unit,
    onCancelRequest: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        when {
            uiState.pendingRequestFrom != null -> {
                Text("Partner Request", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${uiState.pendingRequestFrom.name} wants to link with you.")
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDeclineRequest, modifier = Modifier.weight(1f)) { Text("Decline") }
                    Button(onClick = onAcceptRequest, modifier = Modifier.weight(1f)) { Text("Accept") }
                }
            }
            uiState.pendingRequestTo != null -> {
                Text("Request Sent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Waiting for ${uiState.pendingRequestTo.name} to accept.")
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = onCancelRequest, modifier = Modifier.fillMaxWidth()) { Text("Cancel Request") }
            }
            else -> {
                Text("Link a Partner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Enter your partner's 12-digit ID to send a request.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = uiState.partnerShortIdInput,
                    onValueChange = onShortIdChange,
                    label = { Text("Partner ID") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSendRequest, modifier = Modifier.fillMaxWidth(), enabled = uiState.partnerShortIdInput.length == 12) {
                    Text("Send Request")
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Your ID: ${uiState.currentUser?.shortId ?: "..."}", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PartnerMapContent(partner: User?, anniversaryDate: Long?, onEditAnniversary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        MapContent(modifier = Modifier.fillMaxSize(), partner = partner)

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (anniversaryDate != null) {
                AnniversaryBadge(anniversaryDate, onClick = onEditAnniversary)
            }
            if (partner != null) {
                PartnerCard(partner)
            }
        }
    }
}

@Composable
fun AnniversaryBadge(timestamp: Long, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            val days = (Clock.System.now().toEpochMilliseconds() - timestamp) / (1000 * 60 * 60 * 24)
            Text(
                text = "Together for $days days",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun PartnerCard(partner: User) {
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(8.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(partner.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(partner.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    partner.batteryLevel?.let { level ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(level * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (level < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (partner.isCharging == true) {
                            Text(" ⚡", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                val lastUpdateText = partner.lastLocationUpdate?.let {
                    val dt = kotlin.time.Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault())
                    "Last seen: ${dt.hour}:${dt.minute.toString().padStart(2, '0')}"
                } ?: "Location unknown"
                Text(lastUpdateText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
