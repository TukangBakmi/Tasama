package com.example.tasama.presentation.partner.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.tasama.presentation.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock

private data class NudgeParticle(
    val emoji: String,
    val initialX: Float,
    val initialY: Float,
    val speedX: Float,
    val speedY: Float,
    val scale: Float,
    val color: Color
)

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun FullScreenNudgeOverlay(
    nudgeType: String?,
    senderName: String?,
    onAnimationDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalIsDarkTheme.current
    
    // Preserve the active nudge type during exit animation to prevent frame-flashing!
    var activeType by remember { mutableStateOf(nudgeType ?: "LOVE") }
    var activeSender by remember { mutableStateOf(senderName) }

    LaunchedEffect(nudgeType, senderName) {
        if (nudgeType != null) {
            activeType = nudgeType
            activeSender = senderName
        }
    }

    var isVisible by remember(nudgeType) { mutableStateOf(nudgeType != null) }

    LaunchedEffect(nudgeType) {
        if (nudgeType != null) {
            isVisible = true
            val startTime = Clock.System.now().toEpochMilliseconds()
            var elapsed = 0L
            while (elapsed < 3200L && isVisible) {
                delay(16)
                elapsed = Clock.System.now().toEpochMilliseconds() - startTime
            }
            isVisible = false
            delay(320) // Wait for exit animation to complete cleanly
            onAnimationDismiss()
        } else {
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible && nudgeType != null,
        enter = fadeIn(tween(350)) + scaleIn(initialScale = 0.82f, animationSpec = tween(350)),
        exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.85f, animationSpec = tween(300)),
        modifier = modifier.zIndex(1000f)
    ) {
        val cleanType = activeType.uppercase()
        var progress by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(nudgeType) {
            progress = 0f
            val startTime = Clock.System.now().toEpochMilliseconds()
            while (progress < 1f) {
                val elapsed = Clock.System.now().toEpochMilliseconds() - startTime
                progress = (elapsed / 3200f).coerceIn(0f, 1f)
                delay(16)
            }
        }

        // Theme-aware Config per Nudge Type
        val (titleText, emojis, bgOverlayColor, mainSymbol) = when (cleanType) {
            "MEAL", "MAKAN" -> Quadruple(
                "Time for a Meal! 🍱",
                listOf("🍱", "🍕", "🍜", "🍔", "🍰", "🍙", "🥗"),
                if (isDarkTheme) Color(0xFF2A1B08).copy(alpha = 0.94f) else Color(0xFFFFF8E1).copy(alpha = 0.94f),
                "🍱"
            )
            "SLEEP", "TIDUR" -> Quadruple(
                "Sleep Tight & Rest Well! 🌙",
                listOf("🌙", "⭐", "✨", "💤", "☁️", "🌌"),
                if (isDarkTheme) Color(0xFF0D1137).copy(alpha = 0.95f) else Color(0xFF1A237E).copy(alpha = 0.92f),
                "🌙"
            )
            "HUG" -> Quadruple(
                "Sending You a Warm Hug! 🫂",
                listOf("🫂", "🤗", "💖", "🌸", "✨"),
                if (isDarkTheme) Color(0xFF2A0818).copy(alpha = 0.94f) else Color(0xFFFCE4EC).copy(alpha = 0.94f),
                "🫂"
            )
            "PING" -> Quadruple(
                "Hey! Whatcha Doing? ⚡",
                listOf("⚡", "💥", "✨", "🔥", "💫"),
                if (isDarkTheme) Color(0xFF2E1A04).copy(alpha = 0.94f) else Color(0xFFFFF3E0).copy(alpha = 0.94f),
                "⚡"
            )
            "COFFEE", "KOPI" -> Quadruple(
                "Take a Coffee Break! ☕",
                listOf("☕", "🍩", "✨", "🥐", "🧁"),
                if (isDarkTheme) Color(0xFF1E1612).copy(alpha = 0.94f) else Color(0xFFEFEBE9).copy(alpha = 0.94f),
                "☕"
            )
            "WATER", "MINUM" -> Quadruple(
                "Stay Hydrated! 💧",
                listOf("💧", "🌊", "🧊", "✨", "🥤"),
                if (isDarkTheme) Color(0xFF06222A).copy(alpha = 0.94f) else Color(0xFFE0F7FA).copy(alpha = 0.94f),
                "💧"
            )
            else -> Quadruple(
                "Thinking of You! ❤️",
                listOf("❤️", "💖", "💕", "💘", "✨", "🌹"),
                if (isDarkTheme) Color(0xFF2A0812).copy(alpha = 0.94f) else Color(0xFFFCE4EC).copy(alpha = 0.94f),
                "❤️"
            )
        }

        val particles = remember(nudgeType) {
            val palette = listOf(
                Color(0xFFFF4081), Color(0xFFFFC107), Color(0xFF00E5FF),
                Color(0xFF76FF03), Color(0xFFE040FB), Color(0xFFFF6D00)
            )
            List(24) {
                NudgeParticle(
                    emoji = emojis.random(),
                    initialX = Random.nextFloat(),
                    initialY = Random.nextFloat() * 0.7f + 0.15f,
                    speedX = (Random.nextFloat() - 0.5f) * 0.4f,
                    speedY = -(Random.nextFloat() * 0.5f + 0.2f),
                    scale = Random.nextFloat() * 0.6f + 0.8f,
                    color = palette.random()
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgOverlayColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isVisible = false
                    onAnimationDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            // UNIQUE CANVAS ANIMATIONS PER NUDGE TYPE
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)

                when (cleanType) {
                    "MEAL", "MAKAN" -> {
                        // Confetti Party Popper Explosion
                        particles.forEachIndexed { i, p ->
                            val currX = (p.initialX + p.speedX * progress) * w
                            val currY = (p.initialY + p.speedY * progress) * h
                            val alpha = (1f - progress).coerceIn(0f, 1f)
                            rotate(degrees = progress * 360f * (if (i % 2 == 0) 1 else -1), pivot = Offset(currX, currY)) {
                                drawRect(
                                    color = p.color.copy(alpha = alpha * 0.85f),
                                    topLeft = Offset(currX - 8.dp.toPx(), currY - 8.dp.toPx()),
                                    size = Size(16.dp.toPx() * p.scale, 8.dp.toPx() * p.scale)
                                )
                            }
                        }
                    }

                    "SLEEP", "TIDUR" -> {
                        // Starry Night & Twinkling 4-Point Stars
                        particles.forEachIndexed { i, p ->
                            val starX = p.initialX * w
                            val starY = p.initialY * h
                            val starAlpha = ((sin((progress * 12f) + i) + 1f) / 2f) * (1f - progress)
                            val starSize = 12.dp.toPx() * p.scale

                            val path = Path().apply {
                                moveTo(starX, starY - starSize)
                                quadraticTo(starX, starY, starX + starSize, starY)
                                quadraticTo(starX, starY, starX, starY + starSize)
                                quadraticTo(starX, starY, starX - starSize, starY)
                                quadraticTo(starX, starY, starX, starY - starSize)
                                close()
                            }
                            drawPath(path, Color(0xFFFFD54F).copy(alpha = starAlpha.coerceIn(0f, 1f)))
                        }
                    }

                    "HUG" -> {
                        // Expanding Heart Wave Embraces
                        for (ring in 1..4) {
                            val ringRadius = (progress * (w * 0.6f) + (ring * 50.dp.toPx())) % (w * 0.6f)
                            val ringAlpha = (1f - (ringRadius / (w * 0.6f))).coerceIn(0f, 0.6f)
                            drawCircle(
                                color = Color(0xFFFF80AB).copy(alpha = ringAlpha),
                                radius = ringRadius,
                                center = center,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }

                    "PING" -> {
                        // Electric Shockwaves & Lightning Sparks
                        for (ring in 1..3) {
                            val ringRadius = ((progress * 1.5f + (ring * 0.3f)) % 1f) * (w * 0.5f)
                            val ringAlpha = (1f - (ringRadius / (w * 0.5f))).coerceIn(0f, 0.8f)
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = ringAlpha),
                                radius = ringRadius,
                                center = center,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }
                        particles.take(12).forEach { p ->
                            val sparkX = center.x + (p.speedX * progress * w * 1.2f)
                            val sparkY = center.y + (p.speedY * progress * h * 1.2f)
                            val sparkAlpha = (1f - progress).coerceIn(0f, 1f)
                            drawCircle(
                                color = Color(0xFFFFEA00).copy(alpha = sparkAlpha),
                                radius = 6.dp.toPx() * p.scale,
                                center = Offset(sparkX, sparkY)
                            )
                        }
                    }

                    "COFFEE", "KOPI" -> {
                        // Rising Silky Smooth Bezier Coffee Steam Waves & Heat Pulse Rings
                        val alpha = (1f - progress).coerceIn(0f, 1f)

                        // 1. Warm Heat Pulse Rings
                        for (ring in 1..3) {
                            val ringRadius = ((progress * 1.2f + (ring * 0.25f)) % 1f) * (w * 0.45f)
                            val ringAlpha = (1f - (ringRadius / (w * 0.45f))).coerceIn(0f, 0.5f) * alpha
                            drawCircle(
                                color = Color(0xFFD7CCC8).copy(alpha = ringAlpha),
                                radius = ringRadius,
                                center = center,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }

                        // 2. Silky Smooth Quadratic Bezier Steam Waves (100% Screen Height Coverage)
                        val numLines = 8
                        val startY = h + 80.dp.toPx() // Starts below bottom edge to cover bottom 100%
                        val totalHeight = h + 160.dp.toPx()
                        val numSegments = 14
                        val segHeight = totalHeight / numSegments

                        for (line in 0 until numLines) {
                            val steamX = center.x + (line - (numLines - 1) / 2f) * 44.dp.toPx()
                            val steamPath = Path()
                            
                            var prevX = steamX + sin((progress * 6f) + line) * 20.dp.toPx()
                            var prevY = startY
                            steamPath.moveTo(prevX, prevY)

                            for (i in 1..numSegments) {
                                val currY = startY - (i * segHeight)
                                val currX = steamX + sin((progress * 6f) + (i * 0.5f) + line) * 24.dp.toPx()
                                
                                val controlX = (prevX + currX) / 2f + cos((progress * 5f) + i * 0.6f + line) * 12.dp.toPx()
                                val controlY = (prevY + currY) / 2f

                                steamPath.quadraticTo(controlX, controlY, currX, currY)
                                prevX = currX
                                prevY = currY
                            }

                            drawPath(
                                path = steamPath,
                                color = Color(0xFFFFF8E1).copy(alpha = alpha * 0.75f),
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 3. Floating Coffee Beans
                        particles.forEach { p ->
                            val beanX = (p.initialX + p.speedX * progress) * w
                            val beanY = (p.initialY + p.speedY * progress) * h
                            drawCircle(
                                color = Color(0xFF795548).copy(alpha = alpha * 0.8f),
                                radius = 8.dp.toPx() * p.scale,
                                center = Offset(beanX, beanY)
                            )
                        }
                    }

                    "WATER", "MINUM" -> {
                        // Water Ripples & Splash Drops
                        for (ring in 1..4) {
                            val rippleRadius = ((progress + (ring * 0.25f)) % 1f) * (w * 0.55f)
                            val rippleAlpha = (1f - (rippleRadius / (w * 0.55f))).coerceIn(0f, 0.7f)
                            drawCircle(
                                color = Color(0xFF00B0FF).copy(alpha = rippleAlpha),
                                radius = rippleRadius,
                                center = center,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }

                    else -> { // LOVE
                        // Flying Hearts
                        particles.forEach { p ->
                            val heartX = (p.initialX + p.speedX * progress) * w
                            val heartY = (p.initialY + p.speedY * progress) * h
                            val heartAlpha = (1f - progress).coerceIn(0f, 1f)
                            val size = 10.dp.toPx() * p.scale

                            translate(left = heartX, top = heartY) {
                                val heartPath = Path().apply {
                                    moveTo(0f, -size * 0.3f)
                                    cubicTo(-size * 0.5f, -size * 0.8f, -size, -size * 0.2f, 0f, size * 0.6f)
                                    cubicTo(size, -size * 0.2f, size * 0.5f, -size * 0.8f, 0f, -size * 0.3f)
                                    close()
                                }
                                drawPath(heartPath, Color(0xFFFF4081).copy(alpha = heartAlpha))
                            }
                        }
                    }
                }
            }

            // High-Contrast Theme Glass Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.94f else 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .padding(32.dp)
                    .wrapContentSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp)
                ) {
                    Text(
                        text = mainSymbol,
                        fontSize = (76 * (0.85f + (sin(progress * PI.toFloat()) * 0.3f))).sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = titleText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!activeSender.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "from $activeSender",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Tap anywhere to dismiss",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
