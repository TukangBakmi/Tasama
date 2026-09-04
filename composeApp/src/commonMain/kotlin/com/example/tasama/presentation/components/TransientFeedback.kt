package com.example.tasama.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.tasama.domain.model.ChatMessage

sealed class TransientFeedback {
    data class Info(val text: String) : TransientFeedback()
    data class Copy(val text: String) : TransientFeedback()
    data class UndoDelete(
        val text: String,
        val channelId: String?,
        val messageIds: List<String>,
        val messages: List<ChatMessage> = emptyList()
    ) : TransientFeedback()

    data object Hide : TransientFeedback()
}

val LocalTransientFeedbackHandler = staticCompositionLocalOf<(TransientFeedback) -> Unit> {
    { }
}

val LocalTransientFeedback = compositionLocalOf<TransientFeedback?> {
    null
}

val LocalTransientFeedbackActionHandler = staticCompositionLocalOf<(TransientFeedback) -> Unit> {
    { }
}

@Composable
fun AppTransientFeedbackOverlay(
    modifier: Modifier = Modifier,
    showUndoBanner: Boolean = true
) {
    val feedback = LocalTransientFeedback.current
    val actionHandler = LocalTransientFeedbackActionHandler.current

    val isUndoDelete = feedback is TransientFeedback.UndoDelete
    if (isUndoDelete && !showUndoBanner) return

    TransientFeedbackOverlay(
        isVisible = feedback != null && feedback !is TransientFeedback.Hide,
        text = when (val f = feedback) {
            is TransientFeedback.Info -> f.text
            is TransientFeedback.Copy -> f.text
            is TransientFeedback.UndoDelete -> f.text
            else -> ""
        },
        actionLabel = if (isUndoDelete) "UNDO" else null,
        onAction = {
            feedback?.let { actionHandler(it) }
        },
        isUndoBanner = isUndoDelete,
        modifier = modifier
    )
}

@Composable
fun UndoDeleteBanner(
    text: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1C1C1E),
        contentColor = Color.White,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onUndo,
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00A9F4))
            ) {
                Text(
                    text = "UNDO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun TransientFeedbackOverlay(
    isVisible: Boolean,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    isUndoBanner: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Use MutableTransitionState to allow the exit animation to complete before the Popup is removed
    val transitionState = remember { MutableTransitionState(isVisible) }.apply {
        targetState = isVisible
    }

    if (transitionState.currentState || transitionState.targetState) {
        val density = LocalDensity.current
        val bottomPadding = if (isUndoBanner) 0.dp else 64.dp
        val bottomPaddingPx = with(density) { bottomPadding.roundToPx() }

        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(0, -bottomPaddingPx),
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                clippingEnabled = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(),
                modifier = modifier
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                if (isUndoBanner) {
                    Surface(
                        color = Color(0xFF1C1C1E),
                        contentColor = Color.White,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (actionLabel != null) {
                                TextButton(
                                    onClick = onAction,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00A9F4))
                                ) {
                                    Text(
                                        text = actionLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF323232),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .widthIn(min = 100.dp, max = 340.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            
                            if (actionLabel != null) {
                                Spacer(modifier = Modifier.width(16.dp))
                                TextButton(
                                    onClick = onAction,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF81C784))
                                ) {
                                    Text(
                                        text = actionLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
