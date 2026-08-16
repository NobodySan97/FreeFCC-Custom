package com.freefcc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a premium minimalist glassmorphism effect to the component.
 * Uses a soft translucent background and an ultra-thin subtle border to create depth.
 */
fun Modifier.glassmorphism(
    cornerRadius: Dp = 24.dp,
    borderAlpha: Float = 0.12f,
    bgAlpha: Float = 0.06f
) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Color.White.copy(alpha = bgAlpha))
    .border(1.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(cornerRadius))
