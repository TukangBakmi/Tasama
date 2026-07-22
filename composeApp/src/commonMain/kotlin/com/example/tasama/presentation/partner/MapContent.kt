package com.example.tasama.presentation.partner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.Story
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.EtaInfo
import com.example.tasama.domain.repository.TravelMode

@Composable
expect fun MapContent(
    modifier: Modifier = Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place> = emptyList(),
    stories: List<Story> = emptyList(),
    anniversaryDate: Long? = null,
    etaInfo: EtaInfo? = null,
    weatherInfo: com.example.tasama.domain.model.WeatherInfo? = null,
    isWeatherLoading: Boolean = false,
    travelMode: TravelMode = TravelMode.DRIVING,
    isPartnerComingToMe: Boolean = false,
    isEtaLoading: Boolean = false,
    etaError: String? = null,
    onEditAnniversary: () -> Unit = {},
    onAddPlace: (Place) -> Unit = {},
    onDeletePlace: (String) -> Unit = {},
    onAddStory: (Story, List<ByteArray>) -> Unit = { _, _ -> },
    onDeleteStory: (Story) -> Unit = {},
    onUpdateStory: (Story) -> Unit = {},
    onSetTravelMode: (TravelMode) -> Unit = {},
    onUnlink: () -> Unit = {},
    selectedStoryForMap: Story? = null,
    onClearSelectedStory: () -> Unit = {},
    onSaveJourney: (String, String, String, List<ByteArray>) -> Unit = { _, _, _, _ -> },
    currentDayRoute: List<com.example.tasama.domain.model.RoutePoint> = emptyList(),
    isRouteLoading: Boolean = false,
    fetchTodayRoute: () -> Unit = {}
)
