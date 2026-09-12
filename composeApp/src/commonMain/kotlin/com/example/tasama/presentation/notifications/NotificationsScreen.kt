package com.example.tasama.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.Activity
import com.example.tasama.presentation.theme.PrimaryLightBlue
import com.example.tasama.presentation.theme.SecondaryLightBlue
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenLeft()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryLightBlue
            ) {
                NotificationTab(
                    text = "Savings",
                    selected = uiState.selectedTab == 0,
                    hasUnread = uiState.hasUnreadSavings,
                    onClick = { viewModel.onTabSelected(0) }
                )
                NotificationTab(
                    text = "Partner",
                    selected = uiState.selectedTab == 1,
                    hasUnread = uiState.hasUnreadPartner,
                    onClick = { viewModel.onTabSelected(1) }
                )
            }

            val activities = if (uiState.selectedTab == 0) uiState.savingsActivities else uiState.partnerActivities

            if (activities.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notifications yet", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(activities) { activity ->
                        ActivityItem(
                            activity = activity,
                            uid = uiState.currentUserId,
                            onClick = { viewModel.onActivityClicked(activity, onNavigateToDetail) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
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
                Text(text)
                if (hasUnread) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PrimaryLightBlue)
                    )
                }
            }
        }
    )
}

@Composable
fun ActivityItem(
    activity: Activity,
    uid: String?,
    onClick: () -> Unit
) {
    val isUnread = activity.isUnreadFor(uid)
    val displayDetails = remember(activity, uid) {
        val performerName = if (activity.userId == uid) "You" else activity.userName
        val affectedName = if (activity.affectedUserId == uid) "you" else (activity.affectedUserName ?: "someone")
        val isPerformerMe = activity.userId == uid
        val isAffectedMe = activity.affectedUserId == uid
        
        val spaceName = activity.metadata["spaceName"] ?: activity.metadata["spaceId"] ?: "dd"
        
        when (activity.type) {
            "INVITATION_SENT" -> {
                if (isPerformerMe) "You invited ${activity.affectedUserName} to $spaceName"
                else if (isAffectedMe) "$performerName invited you to $spaceName"
                else "$performerName invited ${activity.affectedUserName} to $spaceName"
            }
            "MEMBER_JOINED", "INVITATION_ACCEPTED" -> {
                "$performerName joined $spaceName"
            }
            "MEMBER_LEFT" -> {
                "$performerName left $spaceName"
            }
            "MEMBER_REMOVED" -> {
                if (isAffectedMe) "You were removed from $spaceName"
                else if (isPerformerMe) "You removed ${activity.affectedUserName} from $spaceName"
                else "$performerName removed ${activity.affectedUserName} from $spaceName"
            }
            "OWNERSHIP_TRANSFERRED" -> {
                if (isAffectedMe) "Ownership of $spaceName was transferred to you"
                else "$performerName transferred ownership of $spaceName to $affectedName"
            }
            "TRANSACTION_ADDED" -> {
                val amount = activity.metadata["amount"] ?: ""
                "$performerName added $amount to $spaceName"
            }
            "TRANSACTION_UPDATED" -> {
                "$performerName updated a contribution in $spaceName"
            }
            "TRANSACTION_DELETED" -> {
                "$performerName removed a contribution from $spaceName"
            }
            "SPACE_CREATED" -> {
                "$performerName created savings space $spaceName"
            }
            "SPACE_UPDATED" -> {
                "$performerName updated savings space $spaceName"
            }
            "SPACE_DELETED" -> {
                "$performerName deleted savings space $spaceName"
            }
            "TARGET_DATE_UPDATED" -> {
                "$performerName updated the target date for $spaceName"
            }
            "PLACE_ALERT" -> {
                val placeName = activity.metadata["placeName"] ?: "a place"
                if (activity.details.contains("arrived", ignoreCase = true)) {
                    "$performerName arrived at $placeName"
                } else {
                    "$performerName left $placeName"
                }
            }
            "SIGNAL_LOST" -> {
                "$performerName lost signal"
            }
            "SIGNAL_RESTORED" -> {
                if (isPerformerMe) "Your signal was restored"
                else "$performerName's signal was restored"
            }
            "ANNIVERSARY_UPDATED" -> {
                if (isPerformerMe) "You updated the anniversary date"
                else "$performerName updated the anniversary date"
            }
            "PLACE_ADDED" -> {
                val placeName = activity.metadata["placeName"] ?: "a place"
                "$performerName added a new place: $placeName"
            }
            "PLACE_UPDATED" -> {
                val placeName = activity.metadata["placeName"] ?: "a place"
                "$performerName updated place: $placeName"
            }
            "PLACE_DELETED" -> {
                val placeName = activity.metadata["placeName"] ?: "a place"
                "$performerName deleted place: $placeName"
            }
            "PARTNER_REQUEST" -> {
                if (isPerformerMe) "You sent a partner request to ${activity.affectedUserName}"
                else if (isAffectedMe) "$performerName sent you a partner request"
                else "$performerName sent a partner request to ${activity.affectedUserName}"
            }
            "PARTNER_ACCEPTED" -> {
                if (isPerformerMe) "You and ${activity.affectedUserName} are now partners"
                else if (isAffectedMe) "You and $performerName are now partners"
                else "$performerName and ${activity.affectedUserName} are now partners"
            }
            else -> activity.details
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isUnread) PrimaryLightBlue.copy(alpha = 0.05f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon area
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SecondaryLightBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            ActivityIcon(activity)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = activity.title,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = formatTimestamp(activity.timestamp),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayDetails,
                fontSize = 13.sp,
                color = if (isUnread) Color.Black else Color.DarkGray
            )
        }
    }
}

@Composable
private fun ActivityIcon(activity: Activity) {
    val icon = remember(activity.type, activity.details) {
        when (activity.type) {
            // Savings
            "TRANSACTION_ADDED" -> Icons.Default.Add
            "TRANSACTION_DELETED" -> Icons.Default.Remove
            "INVITATION_SENT" -> Icons.Default.PersonAdd
            "MEMBER_JOINED", "INVITATION_ACCEPTED" -> Icons.Default.Person
            "MEMBER_REMOVED" -> Icons.Default.PersonRemove
            "OWNERSHIP_TRANSFERRED" -> Icons.Default.SwapHoriz
            "SPACE_CREATED" -> Icons.Default.AddCircle
            "SPACE_UPDATED" -> Icons.Default.Edit
            "SPACE_DELETED" -> Icons.Default.Delete
            "TARGET_DATE_UPDATED" -> Icons.Default.Event
            
            // Partner
            "PLACE_ALERT" -> {
                if (activity.details.contains("arrived", ignoreCase = true)) Icons.Default.LocationOn
                else Icons.Default.ExitToApp
            }
            "PLACE_ADDED" -> Icons.Default.AddLocation
            "PLACE_UPDATED" -> Icons.Default.EditLocation
            "PLACE_DELETED" -> Icons.Default.LocationOff
            "PARTNER_REQUEST" -> Icons.Default.Person
            "PARTNER_ACCEPTED" -> Icons.Default.Favorite
            "SIGNAL_LOST" -> Icons.Default.SignalWifiOff
            "SIGNAL_RESTORED" -> Icons.Default.SignalWifi4Bar
            "ANNIVERSARY_UPDATED" -> Icons.Default.Favorite
            
            else -> Icons.Default.Notifications
        }
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = PrimaryLightBlue,
        modifier = Modifier.size(20.dp)
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val timeZone = TimeZone.currentSystemDefault()
    val dateTime = instant.toLocalDateTime(timeZone)
    val now = kotlin.time.Clock.System.now().toLocalDateTime(timeZone)
    val yesterday = kotlin.time.Clock.System.now().minus(24, DateTimeUnit.HOUR).toLocalDateTime(timeZone)

    return when {
        dateTime.date == now.date -> "Today"
        dateTime.date == yesterday.date -> "Yesterday"
        else -> "${dateTime.dayOfMonth.toString().padStart(2, '0')}/${dateTime.monthNumber.toString().padStart(2, '0')}/${dateTime.year}"
    }
}
