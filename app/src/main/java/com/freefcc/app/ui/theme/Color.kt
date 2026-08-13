package com.freefcc.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════
// Primary Dark Theme Palette (Tactical Outdoor Dark)
// ═══════════════════════════════════════════════════════════════════════

val DarkBackground = Color(0xFF0C0E11)
val DarkBackgroundMid = Color(0xFF11151A)
val DarkSurface = Color(0xFF151A20)
val DarkSurfaceElevated = Color(0xFF1A2027)

// Card & Container Outlines
val DarkBorder = Color(0xFF303842)
val DarkBorderVariant = Color(0xFF222A33)

// Brand & Accent Colors
val BrandOrange = Color(0xFFFF9D4D) // Primary action & brand accent
val BrandCyan = Color(0xFF00E5FF)   // Secondary tech cyan highlight
val StatusGreen = Color(0xFF4ED69A)  // Active FCC / Success mint emerald
val StatusAmber = Color(0xFFFFD166)  // Warning / 4G / Beta accent
val StatusRed = Color(0xFFFF5C70)    // Error / Disconnected coral red
val UpdateBlue = Color(0xFF79A8FF)   // Navigation & update blue

// Text Colors (High Contrast for Outdoor Readability)
val TextPrimary = Color(0xFFF5F7FA)   // ~15.8:1 contrast on DarkBackground
val TextSecondary = Color(0xFFA5AFBA) // ~7.2:1 contrast on DarkBackground
val TextMuted = Color(0xFF687581)     // ~3.8:1 contrast on DarkBackground

// Color Aliases for Components and Backward Compatibility
val BgDark = DarkBackground
val BgMid = DarkBackgroundMid
val BgLight = DarkSurfaceElevated
val CardBg = DarkSurface
val CardBorder = DarkBorder
val Cyan = BrandCyan
val Orange = BrandOrange
val Green = StatusGreen
val Amber = StatusAmber
val Red = StatusRed
val TextWhite = TextPrimary
val TextGray = TextSecondary
val TextDim = TextMuted

// Ambient Brushes & Gradients
val BackgroundGradient = Brush.verticalGradient(
    colors = listOf(DarkBackground, DarkBackgroundMid, DarkBackground)
)

val CardBackgroundGradient = Brush.verticalGradient(
    colors = listOf(DarkSurface, Color(0xFF11161D))
)

fun cardBorderGradient(borderColor: Color = DarkBorder) = Brush.horizontalGradient(
    listOf(
        borderColor.copy(alpha = 0.5f),
        borderColor.copy(alpha = 0.15f),
        borderColor.copy(alpha = 0.4f)
    )
)

// ═══════════════════════════════════════════════════════════════════════
// Custom Extended Palette for Drone Statuses
// ═══════════════════════════════════════════════════════════════════════

@Immutable
data class FreeFccExtraColors(
    val brandOrange: Color = BrandOrange,
    val brandCyan: Color = BrandCyan,
    val statusGreen: Color = StatusGreen,
    val statusAmber: Color = StatusAmber,
    val statusRed: Color = StatusRed,
    val updateBlue: Color = UpdateBlue,
    val cardBackground: Color = DarkSurface,
    val cardBorder: Color = DarkBorder,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textMuted: Color = TextMuted
)

val LocalFreeFccExtraColors = staticCompositionLocalOf { FreeFccExtraColors() }
