package com.mrhakan.codexmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF04222E),
    secondary = Color(0xFF94A3B8),
    background = Color(0xFF0B1120),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF475569),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
)

@Composable
fun CodexMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
