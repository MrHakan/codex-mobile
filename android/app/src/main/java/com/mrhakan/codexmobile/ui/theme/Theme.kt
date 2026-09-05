package com.mrhakan.codexmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Near-monochrome palette in the spirit of the Codex desktop app: a dark, flat
 * surface, hairline borders, and colour reserved for status and diffs.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFECECEC),
    onPrimary = Color(0xFF0D0D0D),
    secondary = Color(0xFF8F8F8F),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF9B9B9B),
    outline = Color(0xFF2E2E2E),
    error = Color(0xFFF85149),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5A5A5A),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF141414),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF5A5A5A),
    outline = Color(0xFFDCDCDC),
    error = Color(0xFFCF222E),
)

/** Colours that are not part of the Material scheme: status dots and diffs. */
data class CodexAccents(
    val running: Color,
    val ready: Color,
    val failed: Color,
    val diffAdded: Color,
    val diffRemoved: Color,
    val diffAddedBackground: Color,
    val diffRemovedBackground: Color,
    val diffHunk: Color,
)

private val DarkAccents = CodexAccents(
    running = Color(0xFFE3A008),
    ready = Color(0xFF3FB950),
    failed = Color(0xFFF85149),
    diffAdded = Color(0xFF7EE787),
    diffRemoved = Color(0xFFFF7B72),
    diffAddedBackground = Color(0x1A3FB950),
    diffRemovedBackground = Color(0x1AF85149),
    diffHunk = Color(0xFF79C0FF),
)

private val LightAccents = CodexAccents(
    running = Color(0xFF9A6700),
    ready = Color(0xFF1A7F37),
    failed = Color(0xFFCF222E),
    diffAdded = Color(0xFF116329),
    diffRemoved = Color(0xFF82071E),
    diffAddedBackground = Color(0x1A1A7F37),
    diffRemovedBackground = Color(0x1ACF222E),
    diffHunk = Color(0xFF0550AE),
)

val LocalCodexAccents: ProvidableCompositionLocal<CodexAccents> =
    staticCompositionLocalOf { DarkAccents }

@Composable
fun CodexMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCodexAccents provides if (darkTheme) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
