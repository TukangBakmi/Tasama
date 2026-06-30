package com.example.tasama.presentation.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.model.User
import com.example.tasama.presentation.components.UserAvatar
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.PickerResultLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import tasama.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = com.example.tasama.presentation.main.LocalSnackbarHostState.current
    val clipboardManager = LocalClipboardManager.current

    val pickerLauncher = rememberFilePickerLauncher(
        type = PickerType.Image,
        title = "Select Profile Picture",
        onResult = { file: PlatformFile? ->
            if (file != null) {
                viewModel.uploadProfilePicture(file)
            }
        }
    )

    // Side effect handlers using snapshotFlow to prevent cancellation when state is cleared
    LaunchedEffect(Unit) {
        launch {
            snapshotFlow { uiState.exportMessage }
                .filterNotNull()
                .collect { message ->
                    viewModel.clearExportMessage()
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Profile") })
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        var showThemeDialog by remember { mutableStateOf(false) }
        var showCurrencyDialog by remember { mutableStateOf(false) }
        var showLinkPartnerDialog by remember { mutableStateOf(false) }
        var showUnlinkConfirmDialog by remember { mutableStateOf(false) }
        var showEditNameDialog by remember { mutableStateOf(false) }
        var showAvatarSelectionDialog by remember { mutableStateOf(false) }

        ProfileContent(
            uiState = uiState,
            onExportExcel = viewModel::exportToExcel,
            onExportPdf = viewModel::exportToPdf,
            onLogout = viewModel::logout,
            onCopyId = { id ->
                clipboardManager.setText(AnnotatedString(id))
                viewModel.onIdCopied()
            },
            onEditName = { showEditNameDialog = true },
            onEditAvatar = { showAvatarSelectionDialog = true },
            onDeleteAvatar = viewModel::deleteProfilePicture,
            onThemeClick = { showThemeDialog = true },
            onCurrencyClick = { showCurrencyDialog = true },
            onPartnerClick = {
                if (uiState.partnerId == null) {
                    showLinkPartnerDialog = true
                } else {
                    showUnlinkConfirmDialog = true
                }
            },
            modifier = Modifier.padding(padding)
        )

        if (showEditNameDialog) {
            EditNameDialog(
                currentName = uiState.userName,
                onDismiss = { showEditNameDialog = false },
                onConfirm = { newName ->
                    viewModel.updateDisplayName(newName)
                    showEditNameDialog = false
                }
            )
        }

        if (showAvatarSelectionDialog) {
            AvatarSelectionDialog(
                onDismiss = { showAvatarSelectionDialog = false },
                onAvatarSelected = { avatarRes ->
                    // Since updateProfilePicture expects a URL (string),
                    // we'll store the resource name or a specific convention.
                    // For now, let's pass the resource name as the "URL".
                    viewModel.updateProfilePicture(avatarRes)
                    showAvatarSelectionDialog = false
                },
                onDeleteAvatar = {
                    viewModel.deleteProfilePicture()
                    showAvatarSelectionDialog = false
                },
                pickerLauncher = pickerLauncher,
                showDeleteOption = uiState.profilePictureUrl != null
            )
        }

        if (showLinkPartnerDialog) {
            LinkPartnerDialog(
                onDismiss = { showLinkPartnerDialog = false },
                onConfirm = { shortId ->
                    viewModel.linkPartner(shortId)
                    showLinkPartnerDialog = false
                }
            )
        }

        if (showUnlinkConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showUnlinkConfirmDialog = false },
                title = { Text("Unlink Partner") },
                text = { Text("Are you sure you want to unlink from your partner? This will remove the connection for both of you.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.unlinkPartner()
                            showUnlinkConfirmDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Unlink")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnlinkConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showThemeDialog) {
            SettingsSelectionDialog(
                title = "Select Theme",
                options = AppTheme.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                selectedIndex = AppTheme.entries.indexOf(uiState.theme),
                onDismiss = { showThemeDialog = false },
                onSelect = { index ->
                    viewModel.updateTheme(AppTheme.entries[index])
                    showThemeDialog = false
                }
            )
        }

        if (showCurrencyDialog) {
            val currencies = listOf("IDR", "USD", "EUR", "GBP", "JPY")
            SettingsSelectionDialog(
                title = "Select Currency",
                options = currencies,
                selectedIndex = currencies.indexOf(uiState.currency).coerceAtLeast(0),
                onDismiss = { showCurrencyDialog = false },
                onSelect = { index ->
                    viewModel.updateCurrency(currencies[index])
                    showCurrencyDialog = false
                }
            )
        }
    }
}

@Composable
fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
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
fun AvatarSelectionDialog(
    onDismiss: () -> Unit,
    onAvatarSelected: (String) -> Unit,
    onDeleteAvatar: () -> Unit,
    pickerLauncher: PickerResultLauncher,
    showDeleteOption: Boolean
) {
    val avatars = listOf(
        Res.drawable.Avatar1, Res.drawable.Avatar2, Res.drawable.Avatar3,
        Res.drawable.Avatar4, Res.drawable.Avatar5, Res.drawable.Avatar6,
        Res.drawable.Avatar7, Res.drawable.Avatar8, Res.drawable.Avatar9
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Avatar") },
        text = {
            Column {
                Text("Select an avatar from the gallery or pick from your phone.")
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(avatars.size) { index ->
                        val avatarRes = avatars[index]
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .clickable {
                                    // Extract resource name to store it
                                    onAvatarSelected("avatar_${index + 1}")
                                }
                        ) {
                            Image(
                                painter = painterResource(avatarRes),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            pickerLauncher.launch()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gallery")
                    }

                    if (showDeleteOption) {
                        OutlinedButton(
                            onClick = {
                                onDeleteAvatar()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsSelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun LinkPartnerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var shortId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Partner") },
        text = {
            Column {
                Text("Enter your partner's 12-digit ID to link your accounts.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = shortId,
                    onValueChange = {
                        // Only allow numbers and max 12 digits
                        if (it.all { char -> char.isDigit() } && it.length <= 12) {
                            shortId = it
                        }
                    },
                    label = { Text("Partner ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(shortId) },
                enabled = shortId.length == 12
            ) {
                Text("Link")
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
fun ProfileContent(
    uiState: ProfileUiState,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onLogout: () -> Unit,
    onCopyId: (String) -> Unit,
    onEditName: () -> Unit,
    onEditAvatar: () -> Unit,
    onDeleteAvatar: () -> Unit,
    onThemeClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onPartnerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProfileHeader(
                    name = uiState.userName,
                    email = uiState.userEmail,
                    shortId = uiState.userShortId,
                    profilePictureUrl = uiState.profilePictureUrl,
                    isGuest = uiState.isGuest,
                    onCopyId = onCopyId,
                    onEditName = onEditName,
                    onEditAvatar = onEditAvatar
                )
            }

            if (uiState.isGuest) {
                item {
                    GuestWarningCard(onLogin = onLogout)
                }
            } else {
                item {
                    SectionTitle("Relationship")
                }
                item {
                    ProfileMenuItem(
                        icon = Icons.Default.Favorite,
                        title = "Partner",
                        subtitle = uiState.partnerName ?: "Not linked (Tap to link)",
                        onClick = onPartnerClick
                    )
                }

                item {
                    SectionTitle("Data Export")
                }
                item {
                    ProfileMenuItem(
                        icon = Icons.Default.Share,
                        title = "Export to Excel",
                        onClick = onExportExcel
                    )
                }
                item {
                    ProfileMenuItem(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Export to PDF",
                        onClick = onExportPdf
                    )
                }
            }

            item {
                SectionTitle("Settings")
            }
            item {
                ProfileMenuItem(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = uiState.theme.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick = onThemeClick
                )
            }
            item {
                ProfileMenuItem(
                    icon = Icons.Default.AttachMoney,
                    title = "Currency",
                    subtitle = uiState.currency,
                    onClick = onCurrencyClick
                )
            }
            item {
                ProfileMenuItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = "Logout",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = onLogout
                )
            }
        }

        if (uiState.isExporting || uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (uiState.isExporting) "Exporting data..." else "Loading...")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(
    name: String,
    email: String,
    shortId: String,
    profilePictureUrl: String?,
    isGuest: Boolean,
    onCopyId: (String) -> Unit,
    onEditName: () -> Unit,
    onEditAvatar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(80.dp)
        ) {
            UserAvatar(
                user = User(name = name, avatarUrl = profilePictureUrl),
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!isGuest) {
                            Modifier.clickable { onEditAvatar() }
                        } else Modifier
                    ),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (!isGuest) {
                // Add a small edit icon
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onEditAvatar() }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Profile Picture",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !isGuest) { onEditName() }
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!isGuest) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Name",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (shortId.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onCopyId(shortId) }
                        .padding(vertical = 2.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ID: $shortId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy ID",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun GuestWarningCard(onLogin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Guest Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You are currently logged in as a guest. Your data is temporary. Sign in to sync your data across devices and link with a partner.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login / Sign Up")
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (titleColor == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else titleColor
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Preview
@Composable
fun ProfilePreview() {
    MaterialTheme {
        ProfileContent(
            uiState = ProfileUiState(),
            onExportExcel = {},
            onExportPdf = {},
            onLogout = {},
            onCopyId = {},
            onEditName = {},
            onEditAvatar = {},
            onDeleteAvatar = {},
            onThemeClick = {},
            onCurrencyClick = {},
            onPartnerClick = {}
        )
    }
}
