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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
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
    val pagerState = rememberPagerState(initialPage = uiState.selectedTab, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // Sync Pager state to ViewModel when user swipes
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onTabSelected(pagerState.currentPage)
    }

    // Sync ViewModel state to Pager (e.g. if updated from elsewhere)
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
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryLightBlue
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
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = Color.LightGray.copy(alpha = 0.5f)
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
        getActivityDescription(activity, uid)
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
            Text(
                text = displayDetails,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTimestamp(activity.timestamp),
                fontSize = 12.sp,
                color = Color.Gray
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

    val timeStr = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"

    return when {
        dateTime.date == now.date -> "Today, $timeStr"
        dateTime.date == yesterday.date -> "Yesterday, $timeStr"
        else -> {
            val monthStr = when(dateTime.monthNumber) {
                1 -> "Jan"
                2 -> "Feb"
                3 -> "Mar"
                4 -> "Apr"
                5 -> "May"
                6 -> "Jun"
                7 -> "Jul"
                8 -> "Aug"
                9 -> "Sep"
                10 -> "Oct"
                11 -> "Nov"
                12 -> "Dec"
                else -> ""
            }
            "$monthStr ${dateTime.dayOfMonth}, ${dateTime.year}, $timeStr"
        }
    }
}

fun getActivityDescription(activity: Activity, uid: String?): String {
    val performerName = if (activity.userId == uid) "You" else activity.userName
    val affectedName = if (activity.affectedUserId == uid) "you" else (activity.affectedUserName ?: "someone")
    val isPerformerMe = activity.userId == uid
    val isAffectedMe = activity.affectedUserId == uid
    
    val spaceName = activity.metadata["spaceName"] ?: activity.metadata["spaceId"] ?: "Space"
    
    return when (activity.type) {
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
        "TARGET_AMOUNT_UPDATED" -> {
            "$performerName updated the target amount for $spaceName"
        }
        "DUE_DATE_UPDATED" -> {
            "$performerName updated the due date for $spaceName"
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
