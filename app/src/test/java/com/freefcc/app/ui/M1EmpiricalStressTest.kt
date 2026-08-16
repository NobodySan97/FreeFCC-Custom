package com.freefcc.app.ui

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import com.freefcc.app.ui.theme.DarkBorderVariant
import com.freefcc.app.ui.theme.DarkSurface
import com.freefcc.app.ui.theme.DarkSurfaceElevated
import com.freefcc.app.ui.theme.FreeFccExtraColors
import com.freefcc.app.ui.theme.FreeFccShapes
import com.freefcc.app.ui.theme.FreeFccTypography
import com.freefcc.app.ui.theme.Green
import com.freefcc.app.ui.theme.LocalFreeFccExtraColors
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
import com.freefcc.app.ui.theme.UpdateBlue
import com.freefcc.app.ui.theme.cardBorderGradient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Stress Harness for Milestone M1 (Theme & Reusable Components).
 */
class M1EmpiricalStressTest {

    @Test
    fun testPaletteAliasesAndContrastTokens() {
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

        // Verify Color objects
        assertEquals(Color(0xFF0F172A).value, DarkBackground.value)
        assertEquals(Color(0xFF1E293B).value, DarkBackgroundMid.value)
        assertEquals(Color(0x10FFFFFF).value, DarkSurface.value)
        assertEquals(Color(0x1FFFFFFF).value, DarkSurfaceElevated.value)
        assertEquals(Color(0x1FFFFFFF).value, DarkBorder.value)
        assertEquals(Color(0x0AFFFFFF).value, DarkBorderVariant.value)
        assertEquals(Color(0xFF3B82F6).value, BrandOrange.value)
        assertEquals(Color(0xFF60A5FA).value, BrandCyan.value)
        assertEquals(Color(0xFF10B981).value, StatusGreen.value)
        assertEquals(Color(0xFFF59E0B).value, StatusAmber.value)
        assertEquals(Color(0xFFEF4444).value, StatusRed.value)
        assertEquals(Color(0xFF3B82F6).value, UpdateBlue.value)
    }

    @Test
    fun testCardBorderGradientGeneration() {
        val defaultGradient = cardBorderGradient()
        assertNotNull("Default card border gradient must not be null", defaultGradient)

        val customColor = Color(0xFF123456)
        val customGradient = cardBorderGradient(customColor)
        assertNotNull("Custom card border gradient must not be null", customGradient)
    }

    @Test
    fun testFreeFccExtraColorsCustomization() {
        val defaultColors = FreeFccExtraColors()
        assertEquals(BrandOrange, defaultColors.brandOrange)
        assertEquals(BrandCyan, defaultColors.brandCyan)

        val customColors = FreeFccExtraColors(
            brandOrange = Color.Red,
            brandCyan = Color.Blue,
            statusGreen = Color.Green,
            statusAmber = Color.Yellow,
            statusRed = Color.Magenta,
            updateBlue = Color.Cyan,
            cardBackground = Color.Black,
            cardBorder = Color.Gray,
            textPrimary = Color.White,
            textSecondary = Color.LightGray,
            textMuted = Color.DarkGray
        )

        assertEquals(Color.Red, customColors.brandOrange)
        assertEquals(Color.Blue, customColors.brandCyan)
        assertEquals(Color.Green, customColors.statusGreen)
        assertEquals(Color.Yellow, customColors.statusAmber)
        assertEquals(Color.Magenta, customColors.statusRed)
        assertEquals(Color.Cyan, customColors.updateBlue)

        assertNotNull(LocalFreeFccExtraColors)
    }

    @Test
    fun testTypographyHierarchy() {
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

        assertEquals(FontFamily.Monospace, TechnicalTypography.logText.fontFamily)
        assertEquals(11.sp, TechnicalTypography.logText.fontSize)

        assertEquals(FontFamily.Monospace, TechnicalTypography.telemetryValue.fontFamily)
        assertEquals(12.sp, TechnicalTypography.telemetryValue.fontSize)
        assertEquals(FontWeight.Bold, TechnicalTypography.telemetryValue.fontWeight)
    }

    @Test
    fun testShapeCornerRadiusHierarchy() {
        assertEquals(CornerSize(8.dp), FreeFccShapes.extraSmall.topStart)
        assertEquals(CornerSize(12.dp), FreeFccShapes.small.topStart)
        assertEquals(CornerSize(50), FreeFccShapes.medium.topStart)
        assertEquals(CornerSize(24.dp), FreeFccShapes.large.topStart)
        assertEquals(CornerSize(32.dp), FreeFccShapes.extraLarge.topStart)

        assertEquals(RoundedCornerShape(8.dp), FreeFccShapes.extraSmall)
        assertEquals(RoundedCornerShape(12.dp), FreeFccShapes.small)
        assertEquals(RoundedCornerShape(50), FreeFccShapes.medium)
        assertEquals(RoundedCornerShape(24.dp), FreeFccShapes.large)
        assertEquals(RoundedCornerShape(32.dp), FreeFccShapes.extraLarge)
    }

    @Test
    fun testButtonSizesMonotonicity() {
        assertTrue(GlowButtonSize.COMPACT.height < GlowButtonSize.DEFAULT.height)
        assertTrue(GlowButtonSize.DEFAULT.height < GlowButtonSize.LARGE.height)

        assertTrue(GlowButtonSize.COMPACT.fontSizeSp < GlowButtonSize.DEFAULT.fontSizeSp)
        assertTrue(GlowButtonSize.DEFAULT.fontSizeSp < GlowButtonSize.LARGE.fontSizeSp)

        assertTrue(GlowButtonSize.COMPACT.iconSizeDp < GlowButtonSize.DEFAULT.iconSizeDp)
        assertTrue(GlowButtonSize.DEFAULT.iconSizeDp < GlowButtonSize.LARGE.iconSizeDp)
    }

    @Test
    fun testProgressCoercionLogic() {
        val testCases = listOf(-10.0f, -0.01f, 0.0f, 0.25f, 0.5f, 0.99f, 1.0f, 1.05f, 100.0f)
        for (input in testCases) {
            val coerced = input.coerceIn(0f, 1f)
            assertTrue("Coerced value must be >= 0f", coerced >= 0f)
            assertTrue("Coerced value must be <= 1f", coerced <= 1f)
            val percent = (coerced * 100).toInt()
            assertTrue("Percentage must be between 0 and 100", percent in 0..100)
        }
    }
}
