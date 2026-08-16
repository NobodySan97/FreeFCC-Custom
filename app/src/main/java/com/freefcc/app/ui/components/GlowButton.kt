package com.freefcc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefcc.app.ui.theme.TextPrimary

enum class GlowButtonSize(val height: Dp, val fontSizeSp: Int, val iconSizeDp: Dp) {
    COMPACT(36.dp, 11, 14.dp),
    DEFAULT(46.dp, 14, 18.dp),
    LARGE(64.dp, 16, 22.dp)
}

@Composable
fun GlowButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    filled: Boolean = true,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: GlowButtonSize = GlowButtonSize.DEFAULT,
    icon: ImageVector? = null,
    shape: CornerBasedShape = RoundedCornerShape(50) // Pill-shaped Material 3 defaults
) {
    val buttonModifier = modifier.height(size.height)
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = buttonModifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) color else Color.White.copy(alpha = 0.1f),
            contentColor = if (filled) Color.White else color,
            disabledContainerColor = if (filled) color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
            disabledContentColor = if (filled) Color.White.copy(alpha = 0.4f) else color.copy(alpha = 0.4f)
        ),
        shape = shape,
        border = when {
            !filled && enabled -> BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            filled && enabled -> BorderStroke(1.dp, color.copy(alpha = 0.3f))
            else -> null
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(size.iconSizeDp),
                strokeWidth = 2.dp,
                color = if (filled) Color.White else color
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(size.iconSizeDp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = size.fontSizeSp.sp,
            letterSpacing = (-0.5).sp // tighter modern look
        )
    }
}

@Composable
fun GlowIconButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentDescription: String? = null,
    size: Dp = 40.dp
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = color
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
