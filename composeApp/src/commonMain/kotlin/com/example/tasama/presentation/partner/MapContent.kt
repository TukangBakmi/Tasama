package com.example.tasama.presentation.partner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.EtaInfo

@Composable
expect fun MapContent(
    modifier: Modifier = Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place> = emptyList(),
    anniversaryDate: Long? = null,
    etaInfo: EtaInfo? = null,
    isPartnerComingToMe: Boolean = false,
    isEtaLoading: Boolean = false,
    etaError: String? = null,
    onEditAnniversary: () -> Unit = {},
    onAddPlace: (String, Double, Double, Double) -> Unit = { _, _, _, _ -> },
    onDeletePlace: (String) -> Unit = {},
    onUnlink: () -> Unit = {}
)
