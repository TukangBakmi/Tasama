package com.example.tasama.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.util.formatAmount
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import kotlin.time.Clock

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
    onNavigateToSavings: () -> Unit = {},
    onNavigateToPartner: () -> Unit = {},
    onNavigateToSavingsDetail: (String) -> Unit = {},
    onNavigateToAI: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = com.example.tasama.presentation.main.LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        snapshotFlow { uiState.error }
            .filterNotNull()
            .collect { error ->
                viewModel.clearError()
                snackbarHostState.showSnackbar(error)
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp, 
                end = 16.dp, 
                top = statusBarPadding + 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Header Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashboardHeader(
                        userName = uiState.userName ?: "User",
                        hasUnread = uiState.hasUnreadNotifications,
                        onNotificationsClick = { viewModel.onNotificationsClick() },
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (uiState.isLoading) {
                        ThreeGrayDotsLoading(modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // 2. Savings Overview
            item {
                val recentSpaces = uiState.recentSavingsSpaces.sortedByDescending { it.updatedAt }.take(2)
                SavingsOverviewCard(
                    totalBalance = uiState.totalSavingsBalance,
                    recentSpaces = recentSpaces,
                    onViewAll = onNavigateToSavings,
                    onSpaceClick = onNavigateToSavingsDetail
                )
            }

            // 3. Financial Overview
            item {
                FinancialOverviewSection(
                    summary = uiState.financialSummary,
                    trends = uiState.trendChartData,
                    spaces = uiState.recentSavingsSpaces,
                    selectedSpaceId = uiState.selectedSpaceId,
                    selectedPeriod = uiState.selectedPeriod,
                    onSpaceSelect = { viewModel.onSpaceFilterSelected(it) },
                    onPeriodSelect = { viewModel.onPeriodFilterSelected(it) }
                )
            }

            // 4. Quick Actions
            item {
                QuickActionsRow(
                    onAskAI = onNavigateToAI
                )
            }

            // 5. Invitations & Requests
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (uiState.hasPendingPartnerRequest) {
                        PendingPartnerRequestSection(
                            onViewPartner = onNavigateToPartner
                        )
                    }

                    if (uiState.pendingInvitations.isNotEmpty()) {
                        PendingInvitationsSection(
                            count = uiState.pendingInvitations.size,
                            onViewInvitations = onNavigateToSavings
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (uiState.showNotificationsPanel) {
            NotificationsBottomSheet(
                activities = uiState.recentActivities,
                onDismiss = { viewModel.onDismissNotifications() },
                onActivityClick = { activity ->
                    viewModel.markActivityAsRead(activity.id)
                }
            )
        }
    }
}

@Composable
fun DashboardHeader(
    userName: String,
    hasUnread: Boolean,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy((-4).dp)
        ) {
            Text(
                text = "Hello,",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Box {
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun SavingsOverviewCard(
    totalBalance: Long,
    recentSpaces: List<SavingsSpace>,
    onViewAll: () -> Unit,
    onSpaceClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Savings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                TextButton(onClick = onViewAll) {
                    Text("View All")
                }
            }

            Text(
                text = "Rp ${totalBalance.formatAmount()}",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (recentSpaces.isNotEmpty()) {
                Text(
                    text = "Recent Spaces",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    recentSpaces.forEach { space ->
                        MiniSpaceCard(
                            space = space,
                            modifier = Modifier.weight(1f),
                            onClick = { onSpaceClick(space.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniSpaceCard(
    space: SavingsSpace,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = space.icon,
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Rp ${space.balance.formatAmount()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FinancialOverviewSection(
    summary: FinancialSummary,
    trends: List<MonthlyTrend>,
    spaces: List<SavingsSpace>,
    selectedSpaceId: String?,
    selectedPeriod: FinancialPeriod,
    onSpaceSelect: (String?) -> Unit,
    onPeriodSelect: (FinancialPeriod) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Financial Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            FilterRow(
                spaces = spaces,
                selectedSpaceId = selectedSpaceId,
                selectedPeriod = selectedPeriod,
                onSpaceSelect = onSpaceSelect,
                onPeriodSelect = onPeriodSelect
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FinancialMetricItem("Income", summary.income, Color(0xFF4CAF50), Modifier.weight(1f))
                    FinancialMetricItem("Expense", summary.expense, Color(0xFFF44336), Modifier.weight(1f))
                    FinancialMetricItem("Net", summary.net, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                TrendChart(
                    trends = trends,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
    }
}

@Composable
fun FilterRow(
    spaces: List<SavingsSpace>,
    selectedSpaceId: String?,
    selectedPeriod: FinancialPeriod,
    onSpaceSelect: (String?) -> Unit,
    onPeriodSelect: (FinancialPeriod) -> Unit
) {
    var showSpaceMenu by remember { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Space Filter
        Box {
            FilterChip(
                selected = true,
                onClick = { showSpaceMenu = true },
                label = { 
                    Text(
                        if (selectedSpaceId == null) "All Spaces" 
                        else spaces.find { it.id == selectedSpaceId }?.name ?: "All Spaces",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) }
            )
            DropdownMenu(expanded = showSpaceMenu, onDismissRequest = { showSpaceMenu = false }) {
                DropdownMenuItem(
                    text = { Text("All Spaces") },
                    onClick = { onSpaceSelect(null); showSpaceMenu = false }
                )
                spaces.forEach { space ->
                    DropdownMenuItem(
                        text = { Text(space.name) },
                        onClick = { onSpaceSelect(space.id); showSpaceMenu = false }
                    )
                }
            }
        }

        // Period Filter
        Box {
            FilterChip(
                selected = true,
                onClick = { showPeriodMenu = true },
                label = { 
                    Text(
                        selectedPeriod.name.replace("_", " ").lowercase().capitalize(),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) }
            )
            DropdownMenu(expanded = showPeriodMenu, onDismissRequest = { showPeriodMenu = false }) {
                FinancialPeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(period.name.replace("_", " ").lowercase().capitalize()) },
                        onClick = { onPeriodSelect(period); showPeriodMenu = false }
                    )
                }
            }
        }
    }
}

private fun String.capitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Composable
fun FinancialMetricItem(label: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "Rp ${amount.formatAmount()}", 
            style = MaterialTheme.typography.labelLarge, 
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TrendChart(trends: List<MonthlyTrend>, modifier: Modifier = Modifier) {
    if (trends.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxAmount = remember(trends) {
        val maxVal = trends.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1L
        (if (maxVal <= 0L) 1L else maxVal).toFloat()
    }

    Canvas(modifier = modifier.padding(vertical = 8.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = (width / trends.size) * 0.35f
        val gap = (width / trends.size) * 0.05f

        trends.forEachIndexed { index, trend ->
            val xBase = index * (width / trends.size) + (width / trends.size) * 0.15f
            
            // Income bar (green)
            val incomeHeight = (trend.income.toFloat() / maxAmount) * height
            if (incomeHeight > 0) {
                drawRect(
                    color = Color(0xFF4CAF50).copy(alpha = 0.8f),
                    topLeft = Offset(xBase, height - incomeHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, incomeHeight)
                )
            }

            // Expense bar (red)
            val expenseHeight = (trend.expense.toFloat() / maxAmount) * height
            if (expenseHeight > 0) {
                drawRect(
                    color = Color(0xFFF44336).copy(alpha = 0.8f),
                    topLeft = Offset(xBase + barWidth + gap, height - expenseHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, expenseHeight)
                )
            }
        }
    }
}

@Composable
fun QuickActionsRow(
    onAskAI: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        onClick = onAskAI
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ask Tasama AI",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Get help with your savings and expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PendingPartnerRequestSection(
    onViewPartner: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewPartner() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Pending partner request",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun PendingInvitationsSection(
    count: Int,
    onViewInvitations: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewInvitations() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Pending invitations ($count)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun ThreeGrayDotsLoading(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsBottomSheet(
    activities: List<DashboardActivity>,
    onDismiss: () -> Unit,
    onActivityClick: (DashboardActivity) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            if (activities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No new notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(activities) { activity ->
                        ActivityItem(
                            activity = activity,
                            showDivider = true,
                            onClick = { onActivityClick(activity) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityItem(
    activity: DashboardActivity,
    showDivider: Boolean,
    onClick: (() -> Unit)? = null
) {
    val timeString = remember(activity.timestamp) {
        try {
            val instant = Instant.fromEpochMilliseconds(activity.timestamp)
            val tz = TimeZone.currentSystemDefault()
            val localDateTime = instant.toLocalDateTime(tz)
            val now = Clock.System.now().toLocalDateTime(tz)
            val today = now.date
            val yesterday = today.minus(1, DateTimeUnit.DAY)

            when (localDateTime.date) {
                today -> {
                    val hour = localDateTime.hour.toString().padStart(2, '0')
                    val minute = localDateTime.minute.toString().padStart(2, '0')
                    "$hour:$minute"
                }
                yesterday -> "Yesterday"
                else -> {
                    val day = localDateTime.day.toString().padStart(2, '0')
                    val monthName = when (localDateTime.month.ordinal + 1) {
                        1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
                        5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
                        9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
                        else -> ""
                    }
                    "$day $monthName"
                }
            }
        } catch (_: Exception) { "" }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (activity.isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = activity.icon, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (activity.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = if (activity.isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Normal
                    )
                }
                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activity.isUnread) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = if (activity.isUnread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (activity.isUnread) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
