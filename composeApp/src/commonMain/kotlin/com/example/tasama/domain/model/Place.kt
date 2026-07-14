package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Place(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Double = 100.0, // in meters
    val createdBy: String = "",
    val notifyOnEntry: Boolean = true,
    val notifyOnExit: Boolean = true,
    val color: Long? = null,
    val iconName: String? = null
)
