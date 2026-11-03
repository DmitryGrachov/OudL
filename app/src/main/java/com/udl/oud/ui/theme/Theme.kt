package com.udl.oud.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val InTuneGreen = Color(0xFF4CAF7A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9AE6C),
    onPrimary = Color(0xFF2B1D14),
    primaryContainer = Color(0xFF5A3E26),
    onPrimaryContainer = Color(0xFFF5DDB8),
    secondary = Color(0xFFC8B39A),
    onSecondary = Color(0xFF2B1D14),
    secondaryContainer = Color(0xFF4A3A2C),
    onSecondaryContainer = Color(0xFFF0DFC9),
    tertiary = Color(0xFFE38B5A),
    background = Color(0xFF1E1510),
    onBackground = Color(0xFFEDE0D2),
    surface = Color(0xFF1E1510),
    onSurface = Color(0xFFEDE0D2),
    surfaceVariant = Color(0xFF3A2C22),
    onSurfaceVariant = Color(0xFFCDBBA8),
    outline = Color(0xFF8D7A68),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5228),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DDB8),
    onPrimaryContainer = Color(0xFF2B1D14),
    secondary = Color(0xFF6F5B45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADCC8),
    onSecondaryContainer = Color(0xFF2B1D14),
    tertiary = Color(0xFFB4552A),
    background = Color(0xFFFFF8F1),
    onBackground = Color(0xFF231A13),
    surface = Color(0xFFFFF8F1),
    onSurface = Color(0xFF231A13),
    surfaceVariant = Color(0xFFF0E1D0),
    onSurfaceVariant = Color(0xFF52443A),
    outline = Color(0xFF85746A),
)

@Composable
fun UdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
