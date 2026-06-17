package com.example.tasama.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.presentation.dashboard.CategorySpending

@Composable
fun DonutChart(
    data: List<CategorySpending>,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 32.dp,
    colors: List<Color> = listOf(
        Color(0xFF6200EE), Color(0xFF03DAC6), Color(0xFFBB86FC),
        Color(0xFF3700B3), Color(0xFF018786), Color(0xFFFF0266)
    )
) {
    val total = data.sumOf { it.amount }
    if (total == 0L) return

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            var startAngle = -90f
            data.forEachIndexed { index, item ->
                val sweepAngle = (item.amount.toFloat() / total) * 360f * animatedProgress.value
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = thickness.toPx(), cap = StrokeCap.Butt)
                )
                startAngle += sweepAngle
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Rp ${total.formatDonutAmount()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun Long.formatDonutAmount(): String {
    return if (this >= 1_000_000) {
        "${(this / 100_000) / 10.0}M"
    } else if (this >= 1_000) {
        "${(this / 100) / 10.0}K"
    } else {
        this.toString()
    }
}
