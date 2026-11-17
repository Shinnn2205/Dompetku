package com.dompetku.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EmeraldPrimary = Color(0xFF10B981)
val EmeraldDark = Color(0xFF059669)
val DangerRed = Color(0xFFEF4444)
val WarnAmber = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF38BDF8)

private val DarkColors = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF032217),
    primaryContainer = Color(0xFF0D2E23),
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondaryContainer = Color(0xFF1C2331),
    onSecondaryContainer = Color(0xFFE5E7EB),
    background = Color(0xFF080A0F),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF121620),
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF1B2130),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF232B3B),
    error = DangerRed
)

private val LightColors = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondaryContainer = Color(0xFFEFF2F7),
    onSecondaryContainer = Color(0xFF111827),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626)
)

@Composable
fun DompetKuTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
