package com.freefcc.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════
// Premium Minimalist Glassmorphism Palette (Aerospace Slate)
// ═══════════════════════════════════════════════════════════════════════

val DarkBackground = Color(0xFF0F172A) // Slate 900
val DarkBackgroundMid = Color(0xFF1E293B) // Slate 800
val DarkSurface = Color(0x10FFFFFF) // 6% White for glass effect
val DarkSurfaceElevated = Color(0x1FFFFFFF) // 12% White

// Card & Container Outlines
val DarkBorder = Color(0x1FFFFFFF) // 12% White
val DarkBorderVariant = Color(0x0AFFFFFF) // 4% White

// Brand & Accent Colors
val BrandOrange = Color(0xFF3B82F6) // Replaced with Electric Blue
val BrandCyan = Color(0xFF60A5FA)   // Blue 400
val StatusGreen = Color(0xFF10B981)  // Emerald
val StatusAmber = Color(0xFFF59E0B)  // Amber
val StatusRed = Color(0xFFEF4444)    // Rose
val UpdateBlue = Color(0xFF3B82F6)   // Blue 500

// Text Colors (High Contrast for Outdoor Readability)
val TextPrimary = Color(0xFFF8FAFC)   // Slate 50
val TextSecondary = Color(0xFF94A3B8) // Slate 400
val TextMuted = Color(0xFF64748B)     // Slate 500

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
    colors = listOf(DarkBackground, Color(0xFF0B1120))
)

val CardBackgroundGradient = Brush.verticalGradient(
    colors = listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF))
)

fun cardBorderGradient(borderColor: Color = DarkBorder) = Brush.horizontalGradient(
    listOf(
        Color(0x33FFFFFF),
        Color(0x1AFFFFFF),
        Color(0x26FFFFFF)
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
