package com.freefcc.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.freefcc.app.ui.theme.CardBg
import com.freefcc.app.ui.theme.CardBorder

/**
 * Premium Minimalist GlassCard with frosted effect (replaces old GlowCard).
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    backgroundColor: Color = CardBg,
    gradientEndColor: Color = Color.Transparent, // Unused in glassmorphism but kept for signature
    shape: CornerBasedShape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Surface(
        color = Color.Transparent,
        shape = shape,
        modifier = cardModifier.glassmorphism(
            cornerRadius = 24.dp,
            borderAlpha = 0.12f,
            bgAlpha = 0.06f
        )
    ) {
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}
