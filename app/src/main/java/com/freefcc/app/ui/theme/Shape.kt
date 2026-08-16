package com.freefcc.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FreeFccShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Micro badges & pills
    small = RoundedCornerShape(12.dp),       // Compact buttons & nav tabs
    medium = RoundedCornerShape(50),         // Pill-shaped Material 3 buttons (50%)
    large = RoundedCornerShape(24.dp),       // Inner cards & panels (Glassmorphism)
    extraLarge = RoundedCornerShape(32.dp)   // Main containers
)
