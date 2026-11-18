package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val TacticalDarkScheme = darkColorScheme(
    primary = NvgGreen,
    onPrimary = Color(0xFF03150A),
    primaryContainer = NvgGreenDark,
    onPrimaryContainer = NvgGreenBright,
    secondary = SkyCyan,
    onSecondary = Color(0xFF041421),
    secondaryContainer = SkyCyanDark,
    onSecondaryContainer = SkyCyan,
    tertiary = AmberGlow,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CockpitSurface,
    onSurface = TextPrimary,
    surfaceVariant = CockpitSurfaceElevated,
    onSurfaceVariant = TextMuted,
    outline = CockpitBorder,
    error = DangerRed,
    onError = Color.White,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
)

@Composable
fun NightDriveTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TacticalDarkScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
