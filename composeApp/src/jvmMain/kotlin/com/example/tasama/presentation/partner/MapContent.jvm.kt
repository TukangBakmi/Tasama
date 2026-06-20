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

@Composable
actual fun MapContent(
    modifier: Modifier,
    partner: User?
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text("JVM Map Placeholder")
    }
}
