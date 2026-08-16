package com.freefcc.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefcc.app.ui.components.GlowButtonSize
import com.freefcc.app.ui.theme.Amber
import com.freefcc.app.ui.theme.BgDark
import com.freefcc.app.ui.theme.BgLight
import com.freefcc.app.ui.theme.BgMid
import com.freefcc.app.ui.theme.BrandCyan
import com.freefcc.app.ui.theme.BrandOrange
import com.freefcc.app.ui.theme.CardBg
import com.freefcc.app.ui.theme.CardBorder
import com.freefcc.app.ui.theme.Cyan
import com.freefcc.app.ui.theme.DarkBackground
import com.freefcc.app.ui.theme.DarkBackgroundMid
import com.freefcc.app.ui.theme.DarkBorder
import com.freefcc.app.ui.theme.DarkSurface
import com.freefcc.app.ui.theme.DarkSurfaceElevated
import com.freefcc.app.ui.theme.FreeFccExtraColors
import com.freefcc.app.ui.theme.FreeFccShapes
import com.freefcc.app.ui.theme.FreeFccTypography
import com.freefcc.app.ui.theme.Green
import com.freefcc.app.ui.theme.Orange
import com.freefcc.app.ui.theme.Red
import com.freefcc.app.ui.theme.StatusAmber
import com.freefcc.app.ui.theme.StatusGreen
import com.freefcc.app.ui.theme.StatusRed
import com.freefcc.app.ui.theme.TechnicalTypography
import com.freefcc.app.ui.theme.TextDim
import com.freefcc.app.ui.theme.TextGray
import com.freefcc.app.ui.theme.TextMuted
import com.freefcc.app.ui.theme.TextPrimary
import com.freefcc.app.ui.theme.TextSecondary
import com.freefcc.app.ui.theme.TextWhite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeAndComponentsTest {

    @Test
    fun testColorPaletteValues() {
        assertEquals(Color(0xFF0F172A), DarkBackground)
        assertEquals(Color(0xFF1E293B), DarkBackgroundMid)
        assertEquals(Color(0x10FFFFFF), DarkSurface)
        assertEquals(Color(0x1FFFFFFF), DarkSurfaceElevated)
        assertEquals(Color(0x1FFFFFFF), DarkBorder)
        assertEquals(Color(0xFF3B82F6), BrandOrange)
        assertEquals(Color(0xFF60A5FA), BrandCyan)
        assertEquals(Color(0xFF10B981), StatusGreen)
        assertEquals(Color(0xFFF59E0B), StatusAmber)
        assertEquals(Color(0xFFEF4444), StatusRed)
        assertEquals(Color(0xFFF8FAFC), TextPrimary)
        assertEquals(Color(0xFF94A3B8), TextSecondary)
        assertEquals(Color(0xFF64748B), TextMuted)
    }

    @Test
    fun testColorAliases() {
        assertEquals(DarkBackground, BgDark)
        assertEquals(DarkBackgroundMid, BgMid)
        assertEquals(DarkSurfaceElevated, BgLight)
        assertEquals(DarkSurface, CardBg)
        assertEquals(DarkBorder, CardBorder)
        assertEquals(BrandCyan, Cyan)
        assertEquals(BrandOrange, Orange)
        assertEquals(StatusGreen, Green)
        assertEquals(StatusAmber, Amber)
        assertEquals(StatusRed, Red)
        assertEquals(TextPrimary, TextWhite)
        assertEquals(TextSecondary, TextGray)
        assertEquals(TextMuted, TextDim)
    }

    @Test
    fun testExtraColorsDefaults() {
        val extraColors = FreeFccExtraColors()
        assertEquals(BrandOrange, extraColors.brandOrange)
        assertEquals(BrandCyan, extraColors.brandCyan)
        assertEquals(StatusGreen, extraColors.statusGreen)
        assertEquals(StatusAmber, extraColors.statusAmber)
        assertEquals(StatusRed, extraColors.statusRed)
        assertEquals(DarkSurface, extraColors.cardBackground)
        assertEquals(DarkBorder, extraColors.cardBorder)
        assertEquals(TextPrimary, extraColors.textPrimary)
        assertEquals(TextSecondary, extraColors.textSecondary)
        assertEquals(TextMuted, extraColors.textMuted)
    }

    @Test
    fun testGlowButtonSizeDimensions() {
        assertEquals(36, GlowButtonSize.COMPACT.height.value.toInt())
        assertEquals(11, GlowButtonSize.COMPACT.fontSizeSp)
        assertEquals(14, GlowButtonSize.COMPACT.iconSizeDp.value.toInt())

        assertEquals(46, GlowButtonSize.DEFAULT.height.value.toInt())
        assertEquals(14, GlowButtonSize.DEFAULT.fontSizeSp)
        assertEquals(18, GlowButtonSize.DEFAULT.iconSizeDp.value.toInt())

        assertEquals(64, GlowButtonSize.LARGE.height.value.toInt())
        assertEquals(16, GlowButtonSize.LARGE.fontSizeSp)
        assertEquals(22, GlowButtonSize.LARGE.iconSizeDp.value.toInt())
    }

    @Test
    fun testShapesHierarchy() {
        assertEquals(RoundedCornerShape(8.dp), FreeFccShapes.extraSmall)
        assertEquals(RoundedCornerShape(12.dp), FreeFccShapes.small)
        assertEquals(RoundedCornerShape(50), FreeFccShapes.medium)
        assertEquals(RoundedCornerShape(24.dp), FreeFccShapes.large)
        assertEquals(RoundedCornerShape(32.dp), FreeFccShapes.extraLarge)
    }

    @Test
    fun testTypographyScale() {
        assertEquals(22.sp, FreeFccTypography.displaySmall.fontSize)
        assertEquals(FontWeight.Black, FreeFccTypography.displaySmall.fontWeight)

        assertEquals(20.sp, FreeFccTypography.titleLarge.fontSize)
        assertEquals(FontWeight.Bold, FreeFccTypography.titleLarge.fontWeight)

        assertEquals(16.sp, FreeFccTypography.titleMedium.fontSize)
        assertEquals(FontWeight.Bold, FreeFccTypography.titleMedium.fontWeight)

        assertEquals(14.sp, FreeFccTypography.titleSmall.fontSize)
        assertEquals(FontWeight.SemiBold, FreeFccTypography.titleSmall.fontWeight)

        assertEquals(14.sp, FreeFccTypography.bodyLarge.fontSize)
        assertEquals(13.sp, FreeFccTypography.bodyMedium.fontSize)
        assertEquals(12.sp, FreeFccTypography.bodySmall.fontSize)

        assertEquals(14.sp, FreeFccTypography.labelLarge.fontSize)
        assertEquals(11.sp, FreeFccTypography.labelMedium.fontSize)
        assertEquals(9.sp, FreeFccTypography.labelSmall.fontSize)
    }

    @Test
    fun testTechnicalTypography() {
        assertNotNull(TechnicalTypography.logText)
        assertEquals(11.sp, TechnicalTypography.logText.fontSize)
        assertEquals(FontWeight.Normal, TechnicalTypography.logText.fontWeight)

        assertNotNull(TechnicalTypography.telemetryValue)
        assertEquals(12.sp, TechnicalTypography.telemetryValue.fontSize)
        assertEquals(FontWeight.Bold, TechnicalTypography.telemetryValue.fontWeight)
    }

    @Test
    fun testProgressBoundaryCoercion() {
        val negativeProgress = (-0.5f).coerceIn(0f, 1f)
        val overflowProgress = (2.5f).coerceIn(0f, 1f)
        val exactZero = (0.0f).coerceIn(0f, 1f)
        val exactOne = (1.0f).coerceIn(0f, 1f)

        assertEquals(0f, negativeProgress, 0.001f)
        assertEquals(1f, overflowProgress, 0.001f)
        assertEquals(0f, exactZero, 0.001f)
        assertEquals(1f, exactOne, 0.001f)

        assertEquals(0, (negativeProgress * 100).toInt())
        assertEquals(100, (overflowProgress * 100).toInt())
    }
}
