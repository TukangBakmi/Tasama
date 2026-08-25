package com.example.tasama.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier.padding(horizontal = 16.dp),
        action = if (snackbarData.visuals.actionLabel != null) {
            {
                TextButton(
                    onClick = { snackbarData.performAction() },
                ) {
                    Text(snackbarData.visuals.actionLabel!!)
                }
            }
        } else null,
        dismissAction = {
            IconButton(onClick = { snackbarData.dismiss() }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionContentColor = MaterialTheme.colorScheme.primary,
        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Text(snackbarData.visuals.message)
    }
}
