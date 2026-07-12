package com.example.tasama.presentation.partner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.EtaInfo
import com.example.tasama.domain.repository.TravelMode

@Composable
expect fun MapContent(
    modifier: Modifier = Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place> = emptyList(),
    anniversaryDate: Long? = null,
    etaInfo: EtaInfo? = null,
    weatherInfo: com.example.tasama.domain.model.WeatherInfo? = null,
    isWeatherLoading: Boolean = false,
    travelMode: TravelMode = TravelMode.DRIVING,
    isPartnerComingToMe: Boolean = false,
    isEtaLoading: Boolean = false,
    etaError: String? = null,
    onEditAnniversary: () -> Unit = {},
    onAddPlace: (String, Double, Double, Double) -> Unit = { _, _, _, _ -> },
    onDeletePlace: (String) -> Unit = {},
    onSetTravelMode: (TravelMode) -> Unit = {},
    onUnlink: () -> Unit = {}
)
