package com.freefcc.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefcc.app.ui.theme.CardBorder
import com.freefcc.app.ui.theme.Cyan
import com.freefcc.app.ui.theme.Green
import com.freefcc.app.ui.theme.TextWhite

@Composable
fun FccPowerRingGauge(
    isFccEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = if (isFccEnabled) Green else Cyan
    val infiniteTransition = rememberInfiniteTransition(label = "powerRingTransition")
    val rotationState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringPulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.size(90.dp)) {
            val strokeWidth = 5.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val centerOffset = Offset(size.width / 2, size.height / 2)
            val currentRotation = rotationState.value
            val currentPulseAlpha = pulseAlphaState.value

            drawCircle(
                color = CardBorder.copy(alpha = 0.4f),
                radius = radius,
                style = Stroke(width = strokeWidth)
            )

            rotate(degrees = if (isFccEnabled) currentRotation else 0f, pivot = centerOffset) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            ringColor.copy(alpha = 0.1f),
                            ringColor.copy(alpha = currentPulseAlpha),
                            ringColor.copy(alpha = 0.2f),
                            ringColor.copy(alpha = currentPulseAlpha)
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = if (isFccEnabled) 280f else 180f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isFccEnabled) Icons.Filled.Bolt else Icons.Filled.CellTower,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isFccEnabled) "FCC ⚡" else "CE 🇪🇺",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = if (isFccEnabled) "27-30 dBm" else "20 dBm",
                color = ringColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
