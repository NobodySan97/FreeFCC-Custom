package com.freefcc.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val DarkColorScheme = darkColorScheme(
    primary = BrandOrange,
    onPrimary = DarkBackground,
    primaryContainer = BrandOrange.copy(alpha = 0.2f),
    onPrimaryContainer = BrandOrange,
    secondary = StatusGreen,
    onSecondary = DarkBackground,
    secondaryContainer = StatusGreen.copy(alpha = 0.2f),
    onSecondaryContainer = StatusGreen,
    tertiary = StatusAmber,
    onTertiary = DarkBackground,
    tertiaryContainer = StatusAmber.copy(alpha = 0.2f),
    onTertiaryContainer = StatusAmber,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderVariant,
    error = StatusRed,
    onError = DarkBackground,
    errorContainer = StatusRed.copy(alpha = 0.2f),
    onErrorContainer = StatusRed
)

@Composable
fun FreeFccTheme(
    darkTheme: Boolean = true, // Drone controller default tactical dark theme
    content: @Composable () -> Unit
) {
    val extraColors = FreeFccExtraColors()

    CompositionLocalProvider(
        LocalFreeFccExtraColors provides extraColors
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = FreeFccTypography,
            shapes = FreeFccShapes,
            content = content
        )
    }
}

// Accessor object for custom extra colors
object FreeFccTheme {
    val extraColors: FreeFccExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFreeFccExtraColors.current
}
