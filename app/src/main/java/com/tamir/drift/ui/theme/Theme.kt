package com.tamir.drift.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = DriftColors.Lime,
    onPrimary = DriftColors.Background,
    secondary = DriftColors.Teal,
    onSecondary = DriftColors.Background,
    tertiary = DriftColors.Orange,
    background = DriftColors.Background,
    onBackground = DriftColors.Text,
    surface = DriftColors.Background,
    onSurface = DriftColors.Text,
    surfaceVariant = DriftColors.Background,
    onSurfaceVariant = DriftColors.TextSecondary,
    outline = DriftColors.GlassBorder,
    outlineVariant = DriftColors.Divider,
    error = DriftColors.Orange,
    onError = DriftColors.Background,
)

/** Shapes: glass cards use 20–22 radius. */
val DriftShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(22.dp),
)

/** Dark-only theme; the whole app is forced to RTL regardless of system locale. */
@Composable
fun DriftTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = DarkScheme, typography = DriftTypography, shapes = DriftShapes, content = content)
    }
}
