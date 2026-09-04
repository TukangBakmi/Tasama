package com.example.tasama.presentation.partner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.tasama.presentation.chat.ContactSelectionItem
import com.example.tasama.presentation.chat.ChatListUiState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.BatteryMode
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.DistanceInfo
import com.example.tasama.presentation.components.LocalTransientFeedbackHandler
import com.example.tasama.presentation.components.TransientFeedback
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerScreen(
    viewModel: PartnerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = com.example.tasama.presentation.main.LocalSnackbarHostState.current
    val feedbackHandler = LocalTransientFeedbackHandler.current
    val clipboardManager = LocalClipboardManager.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
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
                uiState.isGuest -> GuestPartnerContent(onLogin = { showLogoutConfirmDialog = true })
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.isLinked -> {
                    if (uiState.settings.partnerMapEnabled) {
                        PartnerMapContent(
                            currentUser = uiState.currentUser,
                            partner = uiState.partner,
                            places = uiState.places,
                            anniversaryDate = uiState.currentUser?.anniversaryDate,
                            distanceInfo = uiState.distanceInfo,
                            weatherInfo = uiState.weatherInfo,
                            isWeatherLoading = uiState.isWeatherLoading,
                            isPartnerComingToMe = uiState.isPartnerComingToMe,
                            isDistanceLoading = uiState.isDistanceLoading,
                            distanceError = uiState.distanceError,
                            settings = uiState.settings,
                            onEditAnniversary = { showDatePicker = true },
                            onAddPlace = { viewModel.addPlace(it) },
                            onDeletePlace = viewModel::deletePlace,
                            onUnlink = viewModel::unlinkPartner,
                            onUpdatePartnerMapEnabled = viewModel::updatePartnerMapEnabled,
                            onUpdateBatteryMode = viewModel::updateBatteryMode,
                            onUpdateSmartFollowEnabled = viewModel::updateSmartFollowEnabled,
                            onUpdateWeatherWidgetEnabled = viewModel::updateWeatherWidgetEnabled,
                            onUpdateDashboardEnabled = viewModel::updateDashboardEnabled,
                            onUpdatePlacesEnabled = viewModel::updatePlacesEnabled,
                            onUpdateReminderNotificationsEnabled = viewModel::updateReminderNotificationsEnabled,
                            onUpdateReminderMarkersEnabled = viewModel::updateReminderMarkersEnabled,
                            onUpdateTrafficLayerEnabled = viewModel::updateTrafficLayerEnabled,
                            onUpdateMapDarkThemeEnabled = viewModel::updateMapDarkThemeEnabled
                        )
                    } else {
                        DisabledPartnerMapContent(
                            settings = uiState.settings,
                            onUpdatePartnerMapEnabled = viewModel::updatePartnerMapEnabled
                        )
                    }
                }
                else -> LinkingContent(
                    uiState = uiState,
                    onSearch = viewModel::searchUser,
                    onConfirm = { shortId ->
                        viewModel.onPartnerShortIdChange(shortId)
                        viewModel.sendPartnerRequest()
                    },
                    onAcceptRequest = { showDatePicker = true },
                    onDeclineRequest = viewModel::declinePartnerRequest,
                    onCancelRequest = viewModel::cancelPartnerRequest,
                    onCopyId = { id ->
                        clipboardManager.setText(AnnotatedString(id))
                        viewModel.onIdCopied(feedbackHandler)
                    },
                    onClearError = viewModel::clearError
                )
            }
        }
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Log out?") },
            text = { Text("Are you sure you want to log out of your account?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
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
fun LinkPartnerDialog(
    uiState: PartnerUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onClearError: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        onSearch(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Partner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search by name or ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                if (uiState.isSearchingUser) {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                uiState.searchedUser?.let { user ->
                    if (!uiState.filteredContacts.any { it.id == user.id }) {
                        Text("Found User", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        ContactSelectionItem(
                            user = user,
                            onSelect = { onConfirm(user.shortId) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                if (uiState.filteredContacts.isNotEmpty()) {
                    Text(
                        if (query.isEmpty()) "Suggested Contacts" else "Contacts",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(uiState.filteredContacts) { contact ->
                            ContactSelectionItem(
                                user = contact,
                                onSelect = { onConfirm(contact.shortId) }
                            )
                        }
                    }
                } else if (!uiState.isSearchingUser && query.isNotEmpty() && uiState.searchedUser == null) {
                    Text(
                        "No users found",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                if (uiState.error != null) {
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun LinkingContent(
    uiState: PartnerUiState,
    onSearch: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onAcceptRequest: () -> Unit,
    onDeclineRequest: () -> Unit,
    onCancelRequest: () -> Unit,
    onCopyId: (String) -> Unit,
    onClearError: () -> Unit
) {
    var showLinkDialog by remember { mutableStateOf(false) }

    if (showLinkDialog) {
        LinkPartnerDialog(
            uiState = uiState,
            onDismiss = { showLinkDialog = false },
            onSearch = onSearch,
            onConfirm = { shortId ->
                onConfirm(shortId)
                showLinkDialog = false
            },
            onClearError = onClearError
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
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
                Text("Connect with your partner to share location and savings.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { showLinkDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Link Partner")
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
    distanceInfo: DistanceInfo?,
    weatherInfo: com.example.tasama.domain.model.WeatherInfo?,
    isWeatherLoading: Boolean,
    isPartnerComingToMe: Boolean,
    isDistanceLoading: Boolean,
    distanceError: String?,
    settings: AppSettings,
    onEditAnniversary: () -> Unit,
    onAddPlace: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onUnlink: () -> Unit,
    onUpdatePartnerMapEnabled: (Boolean) -> Unit,
    onUpdateBatteryMode: (BatteryMode) -> Unit,
    onUpdateSmartFollowEnabled: (Boolean) -> Unit,
    onUpdateWeatherWidgetEnabled: (Boolean) -> Unit,
    onUpdateDashboardEnabled: (Boolean) -> Unit,
    onUpdatePlacesEnabled: (Boolean) -> Unit,
    onUpdateReminderNotificationsEnabled: (Boolean) -> Unit,
    onUpdateReminderMarkersEnabled: (Boolean) -> Unit,
    onUpdateTrafficLayerEnabled: (Boolean) -> Unit,
    onUpdateMapDarkThemeEnabled: (Boolean) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapContent(
            modifier = Modifier.fillMaxSize(),
            currentUser = currentUser,
            partner = partner,
            places = places,
            anniversaryDate = anniversaryDate,
            distanceInfo = distanceInfo,
            weatherInfo = weatherInfo,
            isWeatherLoading = isWeatherLoading,
            isPartnerComingToMe = isPartnerComingToMe,
            isDistanceLoading = isDistanceLoading,
            distanceError = distanceError,
            onEditAnniversary = onEditAnniversary,
            onAddPlace = onAddPlace,
            onDeletePlace = onDeletePlace,
            onUnlink = onUnlink,
            settings = settings,
            onOpenSettings = { showSettings = true }
        )
    }

    if (showSettings) {
        PartnerSettingsSheet(
            settings = settings,
            onUpdatePartnerMapEnabled = onUpdatePartnerMapEnabled,
            onUpdateBatteryMode = onUpdateBatteryMode,
            onUpdateSmartFollowEnabled = onUpdateSmartFollowEnabled,
            onUpdateWeatherWidgetEnabled = onUpdateWeatherWidgetEnabled,
            onUpdateDashboardEnabled = onUpdateDashboardEnabled,
            onUpdatePlacesEnabled = onUpdatePlacesEnabled,
            onUpdateReminderNotificationsEnabled = onUpdateReminderNotificationsEnabled,
            onUpdateReminderMarkersEnabled = onUpdateReminderMarkersEnabled,
            onUpdateTrafficLayerEnabled = onUpdateTrafficLayerEnabled,
            onUpdateMapDarkThemeEnabled = onUpdateMapDarkThemeEnabled,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun DisabledPartnerMapContent(
    settings: AppSettings,
    onUpdatePartnerMapEnabled: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Map,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Partner Map is Disabled",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Background activity, location updates, and map features are currently paused to save battery.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { onUpdatePartnerMapEnabled(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Partner Map")
        }
    }
}

