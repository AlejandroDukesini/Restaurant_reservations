package com.restaurant.reservations.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta del SPA (carbon, graphite, gold, champagne).
val Carbon = Color(0xFF0B0B0D)
val Graphite = Color(0xFF17171B)
val Gold = Color(0xFFC9A45C)
val Champagne = Color(0xFFF3E5C3)
val Muted = Color(0xFFA1A1AA)
val Danger = Color(0xFFFCA5A5)

private val Colors = darkColorScheme(
    primary = Gold,
    onPrimary = Carbon,
    secondary = Champagne,
    onSecondary = Carbon,
    background = Carbon,
    onBackground = Champagne,
    surface = Graphite,
    onSurface = Champagne,
    surfaceVariant = Graphite,
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Carbon
)

@Composable
fun ReservasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
