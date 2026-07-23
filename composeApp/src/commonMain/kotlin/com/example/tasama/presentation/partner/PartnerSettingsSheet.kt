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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.BatteryMode
import com.example.tasama.domain.model.DefaultRouteType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerSettingsSheet(
    settings: AppSettings,
    onUpdatePartnerMapEnabled: (Boolean) -> Unit,
    onUpdateBatteryMode: (BatteryMode) -> Unit,
    onUpdateSmartFollowEnabled: (Boolean) -> Unit,
    onUpdateLiveEtaEnabled: (Boolean) -> Unit,
    onUpdateWeatherWidgetEnabled: (Boolean) -> Unit,
    onUpdateDashboardEnabled: (Boolean) -> Unit,
    onUpdatePlacesEnabled: (Boolean) -> Unit,
    onUpdateReminderNotificationsEnabled: (Boolean) -> Unit,
    onUpdateStoryMarkersEnabled: (Boolean) -> Unit,
    onUpdateReminderMarkersEnabled: (Boolean) -> Unit,
    onUpdateTrafficLayerEnabled: (Boolean) -> Unit,
    onUpdateMapDarkThemeEnabled: (Boolean) -> Unit,
    onUpdateDefaultRouteType: (DefaultRouteType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                SettingsSection(title = "Battery & Performance") {
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

                    SettingsToggleItem(
                        title = "Smart Follow",
                        subtitle = "Automatically keep partner in view",
                        icon = Icons.Default.MyLocation,
                        checked = settings.smartFollowEnabled,
                        onCheckedChange = onUpdateSmartFollowEnabled
                    )
                }

                SettingsSection(title = "Navigation & Widgets") {
                    SettingsSegmentedControl(
                        title = "Default Travel Mode",
                        options = DefaultRouteType.entries,
                        selectedOption = settings.defaultRouteType,
                        onOptionSelected = onUpdateDefaultRouteType,
                        labelProvider = {
                            when (it) {
                                DefaultRouteType.CAR -> "Car"
                                DefaultRouteType.MOTORCYCLE -> "Motorbike"
                                DefaultRouteType.WALKING -> "Walk"
                            }
                        }
                    )

                    SettingsToggleItem(
                        title = "Live ETA",
                        subtitle = "Show estimated arrival time when moving",
                        icon = Icons.Default.Timer,
                        checked = settings.liveEtaEnabled,
                        onCheckedChange = onUpdateLiveEtaEnabled
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

                SettingsSection(title = "Map Layers") {
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
                        title = "Memory Markers",
                        subtitle = "Show stories on the map",
                        icon = Icons.Default.PhotoLibrary,
                        checked = settings.storyMarkersEnabled,
                        onCheckedChange = onUpdateStoryMarkersEnabled
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
                }

                SettingsSection(title = "Notifications") {
                    SettingsToggleItem(
                        title = "Geofence Alerts",
                        subtitle = "Notify when partner enters/leaves places",
                        icon = Icons.Default.NotificationsActive,
                        checked = settings.reminderNotificationsEnabled,
                        onCheckedChange = onUpdateReminderNotificationsEnabled
                    )
                }
            }
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
