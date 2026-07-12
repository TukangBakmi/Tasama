package com.example.tasama.presentation.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.EtaInfo

@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place>,
    anniversaryDate: Long?,
    etaInfo: EtaInfo?,
    weatherInfo: com.example.tasama.domain.model.WeatherInfo?,
    isWeatherLoading: Boolean,
    travelMode: com.example.tasama.domain.repository.TravelMode,
    isPartnerComingToMe: Boolean,
    isEtaLoading: Boolean,
    etaError: String?,
    onEditAnniversary: () -> Unit,
    onAddPlace: (String, Double, Double, Double) -> Unit,
    onDeletePlace: (String) -> Unit,
    onSetTravelMode: (com.example.tasama.domain.repository.TravelMode) -> Unit,
    onUnlink: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text("iOS Map Placeholder")
    }
}
