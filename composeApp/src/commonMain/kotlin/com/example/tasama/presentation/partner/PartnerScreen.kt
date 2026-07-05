package com.example.tasama.presentation.partner

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.presentation.components.UserAvatar
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerScreen(
    viewModel: PartnerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = com.example.tasama.presentation.main.LocalSnackbarHostState.current
    val clipboardManager = LocalClipboardManager.current

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // Side effect handlers using snapshotFlow to prevent cancellation when state is cleared
    LaunchedEffect(Unit) {
        launch {
            snapshotFlow { uiState.successMessage }
                .filterNotNull()
                .collect { message ->
                    viewModel.clearError()
                    snackbarHostState.showSnackbar(message)
                }
        }
        launch {
            snapshotFlow { uiState.error }
                .filterNotNull()
                .collect { error ->
                    viewModel.clearError()
                    snackbarHostState.showSnackbar(error)
                }
        }
    }

    // Dismiss snackbar when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isGuest -> GuestPartnerContent(onLogin = viewModel::logout)
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.isLinked -> PartnerMapContent(
                    currentUser = uiState.currentUser,
                    partner = uiState.partner,
                    places = uiState.places,
                    anniversaryDate = uiState.currentUser?.anniversaryDate,
                    onEditAnniversary = { showDatePicker = true },
                    onAddPlace = viewModel::addPlace,
                    onDeletePlace = viewModel::deletePlace,
                    onUnlink = viewModel::unlinkPartner
                )
                else -> LinkingContent(
                    uiState = uiState,
                    onShortIdChange = viewModel::onPartnerShortIdChange,
                    onSendRequest = viewModel::sendPartnerRequest,
                    onAcceptRequest = { showDatePicker = true },
                    onDeclineRequest = viewModel::declinePartnerRequest,
                    onCancelRequest = viewModel::cancelPartnerRequest,
                    onCopyId = { id ->
                        clipboardManager.setText(AnnotatedString(id))
                        viewModel.onIdCopied()
                    }
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
    onCancelRequest: () -> Unit,
    onCopyId: (String) -> Unit
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
                    onValueChange = {
                        if (it.all { char -> char.isDigit() } && it.length <= 12) {
                            onShortIdChange(it)
                        }
                    },
                    label = { Text("Partner ID") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSendRequest, modifier = Modifier.fillMaxWidth(), enabled = uiState.partnerShortIdInput.length == 12) {
                    Text("Send Request")
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { uiState.currentUser?.shortId?.let { onCopyId(it) } }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your ID: ${uiState.currentUser?.shortId ?: "..."}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy ID",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun PartnerMapContent(
    currentUser: User?,
    partner: User?,
    places: List<Place>,
    anniversaryDate: Long?,
    onEditAnniversary: () -> Unit,
    onAddPlace: (String, Double, Double, Double) -> Unit,
    onDeletePlace: (String) -> Unit,
    onUnlink: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MapContent(
            modifier = Modifier.fillMaxSize(),
            currentUser = currentUser,
            partner = partner,
            places = places,
            anniversaryDate = anniversaryDate,
            onEditAnniversary = onEditAnniversary,
            onAddPlace = onAddPlace,
            onDeletePlace = onDeletePlace,
            onUnlink = onUnlink
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = CircleShape,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = if (places.isEmpty()) "Long press on the map to add a place" else "Long press map to add more places",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            Icon(
                Icons.Default.Favorite,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
fun MovingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}

private fun Double.formatDecimal(digits: Int): String {
    val precision = 10.0.pow(digits)
    return ((this * precision).roundToInt() / precision).toString()
}

