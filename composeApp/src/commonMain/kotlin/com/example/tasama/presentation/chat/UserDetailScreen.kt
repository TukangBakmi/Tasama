package com.example.tasama.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.PresenceState
import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.components.LocalTransientFeedbackHandler
import com.example.tasama.presentation.components.TransientFeedback
import kotlinx.datetime.*
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    uid: String,
    viewModel: ChatViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val otherUser = uiState.otherUser
    val clipboardManager = LocalClipboardManager.current
    val feedbackHandler = LocalTransientFeedbackHandler.current

    LaunchedEffect(uid) {
        viewModel.observeOtherUserStatus(uid)
    }

    if (otherUser == null || otherUser.id != uid) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Contact Info") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    UserAvatar(
                        user = otherUser,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                        fallbackName = otherUser.name
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = otherUser.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val presence = uiState.presence
                    val isOnline = presence is PresenceState.Online
                    val lastSeen = (presence as? PresenceState.Offline)?.lastSeen ?: 0L
                    
                    Text(
                        text = if (isOnline) "Online" else getStatusText(lastSeen),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }

                item {
                    InfoItem(
                        icon = Icons.Default.Person,
                        label = "User ID",
                        value = otherUser.shortId,
                        onTrailingIconClick = {
                            clipboardManager.setText(AnnotatedString(otherUser.shortId))
                            feedbackHandler(TransientFeedback.Copy("User ID copied to clipboard"))
                        },
                        trailingIcon = Icons.Default.ContentCopy
                    )
                }

                item {
                    InfoItem(
                        icon = Icons.Default.Email,
                        label = "Email",
                        value = otherUser.email
                    )
                }

                if (otherUser.partnerId != null) {
                    item {
                        InfoItem(
                            icon = Icons.Default.Favorite,
                            label = "Relationship",
                            value = "Linked with a partner"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            if (trailingIcon != null && onTrailingIconClick != null) {
                IconButton(onClick = onTrailingIconClick) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun getStatusText(lastActive: Long): String {
    if (lastActive == 0L) return "Offline"
    return try {
        val now = Clock.System.now()
        val instant = Instant.fromEpochMilliseconds(lastActive)
        val tz = TimeZone.currentSystemDefault()
        val lastActiveDateTime = instant.toLocalDateTime(tz)
        val nowDateTime = now.toLocalDateTime(tz)
        
        val timeStr = "${lastActiveDateTime.hour.toString().padStart(2, '0')}:${lastActiveDateTime.minute.toString().padStart(2, '0')}"

        when (lastActiveDateTime.date) {
            nowDateTime.date -> "Last seen today at $timeStr"
            nowDateTime.date.minus(DatePeriod(days = 1)) -> "Last seen yesterday at $timeStr"
            else -> {
                val day = lastActiveDateTime.day.toString().padStart(2, '0')
                val month = lastActiveDateTime.month.number.toString().padStart(2, '0')
                val year = lastActiveDateTime.year
                "Last seen $day/$month/$year"
            }
        }
    } catch (_: Exception) {
        "Offline"
    }
}
