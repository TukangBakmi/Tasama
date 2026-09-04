package com.example.tasama.presentation.partner.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.User
import com.example.tasama.presentation.chat.ContactSelectionItem

@Composable
fun LinkPartnerDialog(
    searchedUser: User?,
    isSearchingUser: Boolean,
    filteredContacts: List<User>,
    suggestedContacts: List<User>,
    error: String?,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onConfirm: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        onSearch(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Partner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search by name or ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                if (isSearchingUser) {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                searchedUser?.let { user ->
                    if (!filteredContacts.any { it.id == user.id }) {
                        Text("Found User", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        ContactSelectionItem(
                            user = user,
                            onSelect = { onConfirm(user.shortId) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                if (filteredContacts.isNotEmpty()) {
                    Text(
                        if (query.isEmpty()) "Suggested Contacts" else "Contacts",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(filteredContacts) { contact ->
                            ContactSelectionItem(
                                user = contact,
                                onSelect = { onConfirm(contact.shortId) }
                            )
                        }
                    }
                } else if (!isSearchingUser && query.isNotEmpty() && searchedUser == null) {
                    Text(
                        "No users found",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
