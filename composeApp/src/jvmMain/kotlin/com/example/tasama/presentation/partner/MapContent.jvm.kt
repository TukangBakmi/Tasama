package com.example.tasama.presentation.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.tasama.domain.model.User

import com.example.tasama.domain.model.Story
import com.example.tasama.domain.repository.TravelMode
import com.example.tasama.domain.repository.EtaInfo

@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?,
    places: List<com.example.tasama.domain.model.Place>,
    stories: List<Story>,
    anniversaryDate: Long?,
    etaInfo: EtaInfo?,
    weatherInfo: com.example.tasama.domain.model.WeatherInfo?,
    isWeatherLoading: Boolean,
    travelMode: TravelMode,
    isPartnerComingToMe: Boolean,
    isEtaLoading: Boolean,
    etaError: String?,
    onEditAnniversary: () -> Unit,
    onAddPlace: (com.example.tasama.domain.model.Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onAddStory: (Story, List<ByteArray>) -> Unit,
    onDeleteStory: (Story) -> Unit,
    onUpdateStory: (Story) -> Unit,
    onSetTravelMode: (TravelMode) -> Unit,
    onUnlink: () -> Unit,
    selectedStoryForMap: Story?,
    onClearSelectedStory: () -> Unit,
    onSaveJourney: (String, String, String, List<ByteArray>) -> Unit,
    currentDayRoute: List<com.example.tasama.domain.model.RoutePoint>,
    isRouteLoading: Boolean,
    fetchTodayRoute: () -> Unit,
    settings: com.example.tasama.domain.model.AppSettings,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text("JVM Map Placeholder")
    }
}
