package com.example.tasama.presentation.partner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.DistanceInfo

@Composable
expect fun MapContent(
    modifier: Modifier = Modifier,
    currentUser: User?,
    partner: User?,
    partnerLiveLocation: com.example.tasama.domain.model.LiveLocation? = null,
    places: List<Place> = emptyList(),
    anniversaryDate: Long? = null,
    distanceInfo: DistanceInfo? = null,
    weatherInfo: com.example.tasama.domain.model.WeatherInfo? = null,
    isWeatherLoading: Boolean = false,
    isPartnerComingToMe: Boolean = false,
    isDistanceLoading: Boolean = false,
    distanceError: String? = null,
    onEditAnniversary: () -> Unit = {},
    onAddPlace: (Place) -> Unit = {},
    onDeletePlace: (String) -> Unit = {},
    onUnlink: () -> Unit = {},
    settings: com.example.tasama.domain.model.AppSettings = com.example.tasama.domain.model.AppSettings(),
    onOpenSettings: () -> Unit = {}
)
