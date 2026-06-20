package com.example.tasama.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import com.example.tasama.domain.model.AppTheme
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    // Combined side-effect handler with yield() for attachment safety
    LaunchedEffect(uiState.exportMessage, uiState.error) {
        uiState.exportMessage?.let {
            kotlinx.coroutines.yield()
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
        uiState.error?.let {
            kotlinx.coroutines.yield()
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Profile") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        var showThemeDialog by remember { mutableStateOf(false) }
        var showCurrencyDialog by remember { mutableStateOf(false) }
        var showLinkPartnerDialog by remember { mutableStateOf(false) }
        var showUnlinkConfirmDialog by remember { mutableStateOf(false) }

        ProfileContent(
            uiState = uiState,
            onExportExcel = viewModel::exportToExcel,
            onExportPdf = viewModel::exportToPdf,
            onLogout = viewModel::logout,
            onCopyId = { id ->
                clipboardManager.setText(AnnotatedString(id))
                viewModel.onIdCopied()
            },
            onUpdateProfilePicture = viewModel::updateProfilePicture,
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
                    onValueChange = { if (it.length <= 12) shortId = it },
                    label = { Text("Partner ID") },
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
    onUpdateProfilePicture: (String) -> Unit,
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
                    onCopyId = onCopyId,
                    onUpdateProfilePicture = onUpdateProfilePicture
                )
            }

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
                    icon = Icons.Default.ExitToApp,
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
    onCopyId: (String) -> Unit,
    onUpdateProfilePicture: (String) -> Unit
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        // For now, use a placeholder random avatar URL to simulate picking
                        val randomId = (1..1000).random()
                        onUpdateProfilePicture("https://i.pravatar.cc/300?u=$randomId")
                    },
                contentAlignment = Alignment.Center
            ) {
                if (profilePictureUrl != null) {
                    // For now, if we had Coil/Kamel we would load it here
                    // Image(painter = ..., contentDescription = null, contentScale = ContentScale.Crop)
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Add a small edit icon
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
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
        Spacer(modifier = Modifier.width(20.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
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
            onUpdateProfilePicture = {},
            onThemeClick = {},
            onCurrencyClick = {},
            onPartnerClick = {}
        )
    }
}
