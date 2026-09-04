package com.example.tasama.presentation.savings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.SavingsMember
import com.example.tasama.domain.model.User
import com.example.tasama.presentation.components.AppSnackbar
import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.chat.InfoItem
import com.example.tasama.presentation.components.AppTransientFeedbackOverlay
import com.example.tasama.presentation.components.LocalTransientFeedbackHandler
import com.example.tasama.presentation.components.TransientFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfileBottomSheet(
    member: SavingsMember,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onCopyUserId: (String) -> Unit
) {
    val feedbackHandler = LocalTransientFeedbackHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatar(
                    user = User(name = member.name, avatarUrl = member.avatarUrl),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = member.role.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(24.dp))

                InfoItem(
                    icon = Icons.Default.Person,
                    label = "User ID",
                    value = member.userId.takeLast(12), // Assuming the shortId is the last 12 chars or similar pattern
                    trailingIcon = Icons.Default.ContentCopy,
                    onTrailingIconClick = {
                        onCopyUserId(member.userId)
                        feedbackHandler(TransientFeedback.Copy("User ID copied to clipboard"))
                    }
                )
            
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        onOpenChat(member.userId)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Chat")
                }
            }
            
            // Add the overlay here to ensure it renders on top of the Bottom Sheet
            AppTransientFeedbackOverlay()
        }
    }
}
