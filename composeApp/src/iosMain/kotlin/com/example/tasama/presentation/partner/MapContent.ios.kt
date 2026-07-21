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

import com.example.tasama.domain.model.Story
import com.example.tasama.domain.repository.TravelMode

@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place>,
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
    onAddPlace: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onAddStory: (Story, List<ByteArray>) -> Unit,
    onDeleteStory: (Story) -> Unit,
    onUpdateStory: (Story) -> Unit,
    onSetTravelMode: (TravelMode) -> Unit,
    onUnlink: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text("iOS Map Placeholder")
    }
}
