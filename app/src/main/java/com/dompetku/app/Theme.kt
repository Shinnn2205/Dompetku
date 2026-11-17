package com.dompetku.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

// Brand & Semantic Colors (Minimalis Clean Modern ala Bank Jago / Apple Wallet)
val EmeraldPrimary = Color(0xFF059669)
val EmeraldBright = Color(0xFF10B981)
val EmeraldSoft = Color(0xFFD1FAE5)

val SlateHero = Color(0xFF0F172A)
val SlateHeroEnd = Color(0xFF1E293B)

val NeedsBlue = Color(0xFF2563EB)
val NeedsBlueSoft = Color(0xFFDBEAFE)

val WantsAmber = Color(0xFFD97706)
val WantsAmberSoft = Color(0xFFFEF3C7)

val DangerRed = Color(0xFFDC2626)
val DangerRedSoft = Color(0xFFFEE2E2)

val WarnAmber = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF0284C7)

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = SlateHero,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF2F6),
    onSecondaryContainer = Color(0xFF0F172A),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE5E9F0),
    error = DangerRed
)

private val DarkColors = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF334155),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF131822),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1C2331),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF232B3A),
    error = Color(0xFFEF4444)
)

fun TextStyle.tabular(): TextStyle = this.copy(fontFeatureSettings = "tnum")

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
