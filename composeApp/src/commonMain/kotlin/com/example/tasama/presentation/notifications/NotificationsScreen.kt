package com.example.tasama.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
                            onClick = {
                                val spaceId = activity.metadata["spaceId"]
                                if (spaceId != null) {
                                    onNavigateToDetail("savings_detail/$spaceId")
                                }
                            }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isUnread) PrimaryLightBlue.copy(alpha = 0.05f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon / Avatar placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SecondaryLightBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = activity.userName.take(1).uppercase(),
                color = PrimaryLightBlue,
                fontWeight = FontWeight.Bold
            )
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
                text = activity.details,
                fontSize = 13.sp,
                color = if (isUnread) Color.Black else Color.DarkGray
            )
        }
    }
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
