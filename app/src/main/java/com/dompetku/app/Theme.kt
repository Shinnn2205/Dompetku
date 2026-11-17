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
val InfoBlue = Color(0xFF3B82F6)

private val DarkColors = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF042F21),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF131822),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1B2230),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF283142),
    error = DangerRed
)

private val LightColors = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
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
