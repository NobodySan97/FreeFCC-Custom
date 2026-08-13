package com.freefcc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.freefcc.app.ui.theme.CardBg
import com.freefcc.app.ui.theme.CardBorder

/**
 * Dark container card with styled border glow gradient.
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    backgroundColor: Color = CardBg,
    gradientEndColor: Color = Color(0xFF11161D),
    shape: CornerBasedShape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    borderColor.copy(alpha = 0.5f),
                    borderColor.copy(alpha = 0.15f),
                    borderColor.copy(alpha = 0.4f)
                )
            )
        ),
        modifier = cardModifier.background(
            Brush.verticalGradient(listOf(backgroundColor, gradientEndColor)),
            shape = shape
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
