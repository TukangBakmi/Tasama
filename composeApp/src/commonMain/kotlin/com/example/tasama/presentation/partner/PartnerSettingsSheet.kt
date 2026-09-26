package com.example.tasama.presentation.partner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.BatteryMode
import com.example.tasama.presentation.components.AppTransientFeedbackOverlay
import com.example.tasama.util.isXiaomiDevice
import com.example.tasama.util.openXiaomiSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerSettingsSheet(
    settings: AppSettings,
    onUpdatePartnerMapEnabled: (Boolean) -> Unit,
    onUpdateBatteryMode: (BatteryMode) -> Unit,
    onUpdateSmartFollowEnabled: (Boolean) -> Unit,
    onUpdateWeatherWidgetEnabled: (Boolean) -> Unit,
    onUpdateDashboardEnabled: (Boolean) -> Unit,
    onUpdatePlacesEnabled: (Boolean) -> Unit,
    onUpdateReminderNotificationsEnabled: (Boolean) -> Unit,
    onUpdateReminderMarkersEnabled: (Boolean) -> Unit,
    onUpdateTrafficLayerEnabled: (Boolean) -> Unit,
    onUpdateMapDarkThemeEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Map Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Master Kill Switch
                SettingsToggleItem(
                    title = "Partner Map",
                    subtitle = "Enable or disable all partner map features",
                    icon = Icons.Default.Map,
                    checked = settings.partnerMapEnabled,
                    onCheckedChange = onUpdatePartnerMapEnabled,
                    highlight = true
                )

                if (settings.partnerMapEnabled) {
                    SecondaryTabRow(
                        selectedTabIndex = selectedCategory,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedCategory == 0,
                            onClick = { selectedCategory = 0 },
                            text = { Text("Widgets", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedCategory == 1,
                            onClick = { selectedCategory = 1 },
                            text = { Text("Layers", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedCategory == 2,
                            onClick = { selectedCategory = 2 },
                            text = { Text("System", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedCategory) {
                            0 -> {
                                SettingsSection(title = "General & Widgets") {
                                    SettingsToggleItem(
                                        title = "Smart Follow",
                                        subtitle = "Automatically keep partner in view",
                                        icon = Icons.Default.MyLocation,
                                        checked = settings.smartFollowEnabled,
                                        onCheckedChange = onUpdateSmartFollowEnabled
                                    )

                                    SettingsToggleItem(
                                        title = "Weather Widget",
                                        subtitle = "Show partner's local weather",
                                        icon = Icons.Default.WbSunny,
                                        checked = settings.weatherWidgetEnabled,
                                        onCheckedChange = onUpdateWeatherWidgetEnabled
                                    )

                                    SettingsToggleItem(
                                        title = "Status Dashboard",
                                        subtitle = "Show anniversary and status bar",
                                        icon = Icons.Default.Dashboard,
                                        checked = settings.dashboardEnabled,
                                        onCheckedChange = onUpdateDashboardEnabled
                                    )
                                }
                            }
                            1 -> {
                                SettingsSection(title = "Map Layers & Places") {
                                    SettingsToggleItem(
                                        title = "Traffic Info",
                                        subtitle = "Show real-time traffic conditions",
                                        icon = Icons.Default.Traffic,
                                        checked = settings.trafficLayerEnabled,
                                        onCheckedChange = onUpdateTrafficLayerEnabled
                                    )

                                    SettingsToggleItem(
                                        title = "Force Dark Map",
                                        subtitle = "Always use dark map style",
                                        icon = Icons.Default.DarkMode,
                                        checked = settings.mapDarkThemeEnabled,
                                        onCheckedChange = onUpdateMapDarkThemeEnabled
                                    )

                                    SettingsToggleItem(
                                        title = "Place Reminders",
                                        subtitle = "Show geofence markers",
                                        icon = Icons.Default.PushPin,
                                        checked = settings.reminderMarkersEnabled,
                                        onCheckedChange = onUpdateReminderMarkersEnabled
                                    )

                                    SettingsToggleItem(
                                        title = "Show Saved Places",
                                        subtitle = "Display saved locations on map",
                                        icon = Icons.Default.Place,
                                        checked = settings.placesEnabled,
                                        onCheckedChange = onUpdatePlacesEnabled
                                    )

                                    SettingsToggleItem(
                                        title = "Geofence Alerts",
                                        subtitle = "Notify when partner enters/leaves places",
                                        icon = Icons.Default.NotificationsActive,
                                        checked = settings.reminderNotificationsEnabled,
                                        onCheckedChange = onUpdateReminderNotificationsEnabled
                                    )
                                }
                            }
                            2 -> {
                                SettingsSection(title = "Battery & Tracking Mode") {
                                    SettingsSegmentedControl(
                                        title = "Location Update Mode",
                                        options = BatteryMode.entries,
                                        selectedOption = settings.batteryMode,
                                        onOptionSelected = onUpdateBatteryMode,
                                        labelProvider = {
                                            when (it) {
                                                BatteryMode.PERFORMANCE -> "High"
                                                BatteryMode.BALANCED -> "Balanced"
                                                BatteryMode.BATTERY_SAVER -> "Power Saver"
                                            }
                                        }
                                    )
                                }

                                if (isXiaomiDevice()) {
                                    SettingsSection(title = "Xiaomi / HyperOS Optimization") {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Xiaomi Background Tracking",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "To ensure live location continues tracking when the app is cleared from Recent Apps, please enable 'Autostart' and set Battery Saver to 'No restrictions' in your device settings.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = { openXiaomiSettings() },
                                                    modifier = Modifier.align(Alignment.End),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                                ) {
                                                    Text("Open Settings", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AppTransientFeedbackOverlay()
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
fun <T> SettingsSegmentedControl(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelProvider: (T) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selectedOption,
                    onClick = { onOptionSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(labelProvider(option), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
