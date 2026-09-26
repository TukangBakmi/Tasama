package com.example.tasama.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.Activity
import com.example.tasama.presentation.theme.PrimaryLightBlue
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private data class ActivityIconConfig(
    val icon: ImageVector,
    val containerColor: Color,
    val iconColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = uiState.selectedTab, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // Sync Pager state to ViewModel when user swipes
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onTabSelected(pagerState.currentPage)
    }

    // Sync ViewModel state to Pager
    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(uiState.selectedTab)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenLeft()
        }
    }

    val currentHasUnread = if (pagerState.currentPage == 0) uiState.hasUnreadSavings else uiState.hasUnreadPartner

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notifications",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentHasUnread) {
                        TextButton(onClick = { viewModel.markAllAsReadCurrentTab() }) {
                            Text(
                                "Mark all as read",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                NotificationTab(
                    text = "Savings",
                    selected = pagerState.currentPage == 0,
                    hasUnread = uiState.hasUnreadSavings,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                )
                NotificationTab(
                    text = "Partner",
                    selected = pagerState.currentPage == 1,
                    hasUnread = uiState.hasUnreadPartner,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) { page ->
                val activities = if (page == 0) uiState.savingsActivities else uiState.partnerActivities
                val categoryName = if (page == 0) "Savings" else "Partner"

                if (activities.isEmpty()) {
                    EmptyNotificationsState(categoryName = categoryName)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(activities, key = { it.id }) { activity ->
                            ActivityItem(
                                activity = activity,
                                uid = uiState.currentUserId,
                                onClick = { viewModel.onActivityClicked(activity, onNavigateToDetail) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationTab(
    text: String,
    selected: Boolean,
    hasUnread: Boolean,
    onClick: () -> Unit
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                if (hasUnread) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    )
}

@Composable
fun EmptyNotificationsState(categoryName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No $categoryName Notifications",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "You're all caught up! Activity updates will appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ActivityItem(
    activity: Activity,
    uid: String?,
    onClick: () -> Unit
) {
    val isUnread = activity.isUnreadFor(uid)
    val annotatedDescription = remember(activity, uid) {
        buildActivityAnnotatedString(activity, uid)
    }
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val iconConfig = remember(activity.type, activity.details, surfaceVariant, primaryColor) {
        getActivityIconConfig(activity, surfaceVariant, primaryColor)
    }

    Surface(
        color = if (isUnread) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dynamic Colored Icon Container
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconConfig.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconConfig.icon,
                    contentDescription = null,
                    tint = iconConfig.iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Main Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = annotatedDescription,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatRelativeTimestamp(activity.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Unread indicator dot
            if (isUnread) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

private fun getActivityIconConfig(
    activity: Activity,
    fallbackContainer: Color,
    fallbackIconColor: Color
): ActivityIconConfig {
    return when (activity.type) {
        // Success / Positive (Green)
        "MEMBER_JOINED", "INVITATION_ACCEPTED", "PARTNER_ACCEPTED" -> ActivityIconConfig(
            icon = Icons.Default.Person,
            containerColor = Color(0xFFE8F5E9),
            iconColor = Color(0xFF2E7D32)
        )
        "TRANSACTION_ADDED" -> ActivityIconConfig(
            icon = Icons.Default.Add,
            containerColor = Color(0xFFE8F5E9),
            iconColor = Color(0xFF2E7D32)
        )
        "SIGNAL_RESTORED" -> ActivityIconConfig(
            icon = Icons.Default.SignalWifi4Bar,
            containerColor = Color(0xFFE8F5E9),
            iconColor = Color(0xFF2E7D32)
        )

        // Danger / Negative (Red)
        "MEMBER_REMOVED", "MEMBER_LEFT" -> ActivityIconConfig(
            icon = Icons.Default.PersonRemove,
            containerColor = Color(0xFFFFEBEE),
            iconColor = Color(0xFFC62828)
        )
        "TRANSACTION_DELETED", "SPACE_DELETED", "PLACE_DELETED" -> ActivityIconConfig(
            icon = Icons.Default.Delete,
            containerColor = Color(0xFFFFEBEE),
            iconColor = Color(0xFFC62828)
        )
        "SIGNAL_LOST" -> ActivityIconConfig(
            icon = Icons.Default.SignalWifiOff,
            containerColor = Color(0xFFFFEBEE),
            iconColor = Color(0xFFC62828)
        )

        // Info / Invites / Updates (Blue / Purple)
        "INVITATION_SENT" -> ActivityIconConfig(
            icon = Icons.Default.PersonAdd,
            containerColor = Color(0xFFE3F2FD),
            iconColor = Color(0xFF1565C0)
        )
        "OWNERSHIP_TRANSFERRED" -> ActivityIconConfig(
            icon = Icons.Default.SwapHoriz,
            containerColor = Color(0xFFEDE7F6),
            iconColor = Color(0xFF512DA8)
        )
        "SPACE_CREATED", "SPACE_UPDATED", "TARGET_DATE_UPDATED", "TARGET_AMOUNT_UPDATED", "DUE_DATE_UPDATED" -> ActivityIconConfig(
            icon = Icons.Default.Edit,
            containerColor = Color(0xFFE3F2FD),
            iconColor = Color(0xFF1565C0)
        )

        // Partner / Love / Location (Pink / Teal / Orange)
        "PARTNER_REQUEST" -> ActivityIconConfig(
            icon = Icons.Default.FavoriteBorder,
            containerColor = Color(0xFFFCE4EC),
            iconColor = Color(0xFFC2185B)
        )
        "ANNIVERSARY_UPDATED" -> ActivityIconConfig(
            icon = Icons.Default.Favorite,
            containerColor = Color(0xFFFCE4EC),
            iconColor = Color(0xFFC2185B)
        )
        "PLACE_ALERT" -> {
            val isArrival = activity.details.contains("arrived", ignoreCase = true)
            ActivityIconConfig(
                icon = if (isArrival) Icons.Default.LocationOn else Icons.Default.ExitToApp,
                containerColor = if (isArrival) Color(0xFFE0F2F1) else Color(0xFFFFF3E0),
                iconColor = if (isArrival) Color(0xFF00695C) else Color(0xFFE65100)
            )
        }
        "PLACE_ADDED", "PLACE_UPDATED" -> ActivityIconConfig(
            icon = Icons.Default.AddLocation,
            containerColor = Color(0xFFE0F2F1),
            iconColor = Color(0xFF00695C)
        )

        else -> ActivityIconConfig(
            icon = Icons.Default.Notifications,
            containerColor = fallbackContainer,
            iconColor = fallbackIconColor
        )
    }
}

private fun formatRelativeTimestamp(timestamp: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diffSeconds = (now - timestamp) / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffSeconds < 60 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays == 1L -> "Yesterday"
        diffDays < 7L -> "${diffDays}d ago"
        else -> {
            val instant = Instant.fromEpochMilliseconds(timestamp)
            val tz = TimeZone.currentSystemDefault()
            val dt = instant.toLocalDateTime(tz)
            val month = dt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val hour = dt.hour.toString().padStart(2, '0')
            val minute = dt.minute.toString().padStart(2, '0')
            "$month ${dt.dayOfMonth}, $hour:$minute"
        }
    }
}

fun buildActivityAnnotatedString(activity: Activity, uid: String?): AnnotatedString {
    val performerName = if (activity.userId == uid) "You" else activity.userName
    val affectedName = if (activity.affectedUserId == uid) "you" else (activity.affectedUserName ?: "someone")
    val isPerformerMe = activity.userId == uid
    val isAffectedMe = activity.affectedUserId == uid

    val rawSpaceName = activity.metadata["spaceName"] ?: activity.metadata["spaceId"]
    val spaceName = when {
        rawSpaceName.isNullOrBlank() -> "Space"
        rawSpaceName.equals("Joined the space", ignoreCase = true) -> "Space"
        rawSpaceName.equals("Left the space", ignoreCase = true) -> "Space"
        rawSpaceName.startsWith("Removed ", ignoreCase = true) -> "Space"
        else -> rawSpaceName
    }

    return buildAnnotatedString {
        when (activity.type) {
            "INVITATION_SENT" -> {
                if (isPerformerMe) {
                    append("You invited ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activity.affectedUserName ?: "someone") }
                    append(" to join ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
                } else if (isAffectedMe) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" invited you to join ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" invited ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activity.affectedUserName ?: "someone") }
                    append(" to join ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
                }
            }
            "MEMBER_JOINED", "INVITATION_ACCEPTED" -> {
                if (isPerformerMe) {
                    append("You joined ")
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" joined ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "MEMBER_LEFT" -> {
                if (isPerformerMe) {
                    append("You left ")
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" left ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "MEMBER_REMOVED" -> {
                if (isAffectedMe) {
                    append("You were removed from ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
                } else if (isPerformerMe) {
                    append("You removed ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activity.affectedUserName ?: "someone") }
                    append(" from ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" removed ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activity.affectedUserName ?: "someone") }
                    append(" from ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
                }
            }
            "OWNERSHIP_TRANSFERRED" -> {
                if (isAffectedMe) {
                    append("Ownership of ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
                    append(" was transferred to you")
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" transferred ownership of ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
                    append(" to ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(affectedName) }
                }
            }
            "TRANSACTION_ADDED" -> {
                val amount = activity.metadata["amount"] ?: ""
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                if (amount.isNotBlank()) {
                    append(" added ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(amount) }
                    append(" to ")
                } else {
                    append(" made a contribution to ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "TRANSACTION_UPDATED" -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                append(" updated a contribution in ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "TRANSACTION_DELETED" -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                append(" removed a contribution from ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "SPACE_CREATED" -> {
                if (isPerformerMe) append("You created savings space ")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" created savings space ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "SPACE_UPDATED" -> {
                if (isPerformerMe) append("You updated savings space ")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" updated savings space ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "SPACE_DELETED" -> {
                if (isPerformerMe) append("You deleted savings space ")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" deleted savings space ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "TARGET_DATE_UPDATED", "TARGET_AMOUNT_UPDATED", "DUE_DATE_UPDATED" -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                append(" updated the goals for ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(spaceName) }
            }
            "PLACE_ALERT" -> {
                val isArrival = activity.details.contains("arrived", ignoreCase = true)
                val extractedPlaceName = when {
                    activity.details.contains(" arrived at ", ignoreCase = true) -> {
                        activity.details.substringAfter(" arrived at ", "").trim()
                    }
                    activity.details.contains(" left ", ignoreCase = true) -> {
                        activity.details.substringAfter(" left ", "").trim()
                    }
                    else -> null
                }
                val placeName = activity.metadata["placeName"]
                    ?.ifBlank { null }
                    ?: extractedPlaceName?.ifBlank { null }
                    ?: "a place"

                if (isPerformerMe) {
                    append(if (isArrival) "You arrived at " else "You left ")
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(if (isArrival) " arrived at " else " left ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(placeName) }
            }
            "SIGNAL_LOST" -> {
                if (isPerformerMe) append("Your device lost signal")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append("'s device lost signal")
                }
            }
            "SIGNAL_RESTORED" -> {
                if (isPerformerMe) append("Your device reconnected")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append("'s device reconnected")
                }
            }
            "ANNIVERSARY_UPDATED" -> {
                if (isPerformerMe) append("You updated the anniversary date")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" updated the anniversary date")
                }
            }
            "PLACE_ADDED" -> {
                val placeName = activity.metadata["placeName"] ?: "a place"
                if (isPerformerMe) append("You added a new place: ")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" added a new place: ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(placeName) }
            }
            "PLACE_UPDATED" -> {
                val placeName = activity.metadata["placeName"] ?: "a place"
                if (isPerformerMe) append("You updated place: ")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" updated place: ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(placeName) }
            }
            "PLACE_DELETED" -> {
                val placeName = activity.metadata["placeName"] ?: "a place"
                if (isPerformerMe) append("You deleted place: ")
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" deleted place: ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(placeName) }
            }
            "PARTNER_REQUEST" -> {
                if (isPerformerMe) {
                    append("You sent a partner request to ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activity.affectedUserName ?: "partner") }
                } else if (isAffectedMe) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" sent you a partner request")
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" sent a partner request to ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activity.affectedUserName ?: "partner") }
                }
            }
            "PARTNER_ACCEPTED" -> {
                if (isPerformerMe) {
                    append("You and ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activity.affectedUserName ?: "partner") }
                    append(" are now partners!")
                } else if (isAffectedMe) {
                    append("You and ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" are now partners!")
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(performerName) }
                    append(" and ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activity.affectedUserName ?: "partner") }
                    append(" are now partners!")
                }
            }
            else -> append(activity.details)
        }
    }
}
