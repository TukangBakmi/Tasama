package com.example.tasama.presentation.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.model.TransactionType

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onTransactionClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    DashboardContent(
        uiState = uiState,
        onTransactionClick = onTransactionClick
    )
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onTransactionClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Balance Card
        item {
            BalanceCard(balance = uiState.balance)
        }

        // Summary Rows (Income & Expense)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryBox(
                    title = "Income",
                    amount = uiState.income,
                    icon = Icons.Default.ArrowUpward,
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                SummaryBox(
                    title = "Expense",
                    amount = uiState.expense,
                    icon = Icons.Default.ArrowDownward,
                    containerColor = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Mini Chart / Progress
        item {
            IncomeExpenseProgress(
                income = uiState.income,
                expense = uiState.expense
            )
        }

        // Weekly Spending Bar Chart
        item {
            WeeklyBarChart(weeklySpending = uiState.weeklySpending)
        }

        // Category Spending Pie Chart
        item {
            CategorySpendingChart(categorySpending = uiState.categorySpending)
        }

        // Monthly Trends Chart
        item {
            MonthlyTrendChart(monthlyTrends = uiState.monthlyTrends)
        }

        // Recent Transactions Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onTransactionClick) {
                    Text("See All")
                }
            }
        }

        // Transaction List
        items(uiState.transactions.take(5)) { transaction ->
            TransactionItem(transaction = transaction)
        }
        
        if (uiState.transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions yet", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun BalanceCard(balance: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Rp ${balance.formatAmount()}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun SummaryBox(
    title: String,
    amount: Long,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Text(
                    text = "Rp ${amount.formatAmount()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun IncomeExpenseProgress(income: Long, expense: Long) {
    val total = income + expense
    val expenseRatio = if (total > 0) expense.toFloat() / total.toFloat() else 0f
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Expense Ratio",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LinearProgressIndicator(
            progress = { expenseRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = Color(0xFFC62828),
            trackColor = Color(0xFFE8F5E9)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${(expenseRatio * 100).toInt()}% Expenses", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("${((1f - expenseRatio) * 100).toInt()}% Savings", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun WeeklyBarChart(weeklySpending: List<DailySpending>) {
    val maxSpending = weeklySpending.maxOfOrNull { it.amount } ?: 1L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Weekly Spending",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklySpending.forEach { daily ->
                    Bar(
                        day = daily.day,
                        amount = daily.amount,
                        maxAmount = maxSpending,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun Bar(
    day: String,
    amount: Long,
    maxAmount: Long,
    modifier: Modifier = Modifier
) {
    val targetBarHeightRatio = amount.toFloat() / maxAmount.toFloat()
    val animatedRatio by animateFloatAsState(
        targetValue = targetBarHeightRatio,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000)
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(animatedRatio.coerceAtLeast(0.05f))
                .width(28.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    if (animatedRatio > 0.7f) MaterialTheme.colorScheme.error 
                    else MaterialTheme.colorScheme.primary
                )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = day,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CategorySpendingChart(categorySpending: List<CategorySpending>) {
    if (categorySpending.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            categorySpending.forEach { spending ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(getCategoryEmoji(spending.category, TransactionType.EXPENSE))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(spending.category, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Rp ${spending.amount.formatAmount()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { spending.percentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlyTrendChart(monthlyTrends: List<MonthlyTrend>) {
    if (monthlyTrends.isEmpty()) return
    val maxAmount = monthlyTrends.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Monthly Trends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyTrends.forEach { trend ->
                    TrendBarPair(
                        trend = trend,
                        maxAmount = maxAmount,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(12.dp).background(Color(0xFF4CAF50), RoundedCornerShape(2.dp)))
                Text(" Income", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 16.dp))
                Box(Modifier.size(12.dp).background(Color(0xFFF44336), RoundedCornerShape(2.dp)))
                Text(" Expense", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun TrendBarPair(
    trend: MonthlyTrend,
    maxAmount: Long,
    modifier: Modifier = Modifier
) {
    val incomeRatio = trend.income.toFloat() / maxAmount.toFloat()
    val expenseRatio = trend.expense.toFloat() / maxAmount.toFloat()

    val animatedIncome by animateFloatAsState(
        targetValue = incomeRatio,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000)
    )
    val animatedExpense by animateFloatAsState(
        targetValue = expenseRatio,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000)
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Income Bar
            Box(
                modifier = Modifier
                    .fillMaxHeight(animatedIncome.coerceAtLeast(0.01f))
                    .width(8.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(Color(0xFF4CAF50))
            )
            // Expense Bar
            Box(
                modifier = Modifier
                    .fillMaxHeight(animatedExpense.coerceAtLeast(0.01f))
                    .width(8.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(Color(0xFFF44336))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = trend.month,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon (Simplified)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = getCategoryEmoji(transaction.category, transaction.type))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.note,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = transaction.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Text(
                text = "${if (isExpense) "-" else "+"} Rp ${transaction.amount.formatAmount()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) Color(0xFFC62828) else Color(0xFF2E7D32)
            )
        }
    }
}

fun Long.formatAmount(): String {
    return this.toString().reversed().chunked(3).joinToString(".").reversed()
}

fun getCategoryEmoji(category: String, type: TransactionType): String {
    if (type == TransactionType.INCOME) return "💰"
    
    return when (category.lowercase()) {
        "food", "makan", "minum", "restoran" -> "🍔"
        "transport", "transportasi", "ojek", "bensin" -> "🚗"
        "shopping", "belanja" -> "🛍️"
        "entertainment", "hiburan", "nonton" -> "🎬"
        "bills", "tagihan", "listrik", "air" -> "🧾"
        "health", "kesehatan", "obat" -> "🏥"
        "education", "pendidikan", "sekolah", "kuliah" -> "🎓"
        "gift", "hadiah" -> "🎁"
        "salary", "gaji" -> "💸"
        "investment", "investasi" -> "📈"
        else -> "📦"
    }
}

@Preview
@Composable
fun DashboardPreview() {
    MaterialTheme {
        DashboardContent(
            uiState = DashboardUiState(
                balance = 1250000,
                income = 2000000,
                expense = 750000,
                transactions = listOf(
                    Transaction(id = "1", amount = 50000, type = TransactionType.EXPENSE, category = "Food", note = "Lunch"),
                    Transaction(id = "2", amount = 1000000, type = TransactionType.INCOME, category = "Salary", note = "Monthly")
                ),
                weeklySpending = listOf(
                    DailySpending("Mon", 50000),
                    DailySpending("Tue", 120000),
                    DailySpending("Wed", 80000),
                    DailySpending("Thu", 200000),
                    DailySpending("Fri", 150000),
                    DailySpending("Sat", 300000),
                    DailySpending("Sun", 100000)
                )
            ),
            onTransactionClick = {}
        )
    }
}
