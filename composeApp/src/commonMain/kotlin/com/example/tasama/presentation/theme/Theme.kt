package com.example.tasama.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = compositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLightBlue,
    onPrimary = OnPrimaryLightBlue,
    primaryContainer = PrimaryContainerLightBlue,
    onPrimaryContainer = OnPrimaryContainerLightBlue,
    secondary = SecondaryLightBlue,
    onSecondary = OnSecondaryLightBlue,
    secondaryContainer = SecondaryContainerLightBlue,
    onSecondaryContainer = OnSecondaryContainerLightBlue,
    tertiary = TertiaryLightBlue,
    onTertiary = OnTertiaryLightBlue,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLightBlue,
    onPrimary = Color(0xFF003549),
    primaryContainer = OnPrimaryContainerLightBlue,
    onPrimaryContainer = PrimaryContainerLightBlue,
    secondary = SecondaryLightBlue,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = OnSecondaryContainerLightBlue,
    onSecondaryContainer = SecondaryContainerLightBlue,
    tertiary = TertiaryLightBlue,
    onTertiary = Color(0xFF003547),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
)

@Composable
fun TasamaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
