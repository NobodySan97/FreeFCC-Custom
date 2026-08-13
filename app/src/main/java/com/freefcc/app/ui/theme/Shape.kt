package com.freefcc.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FreeFccShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),  // Micro badges & pills
    small = RoundedCornerShape(8.dp),       // Compact buttons & nav tabs
    medium = RoundedCornerShape(10.dp),      // Default buttons & auto toggles
    large = RoundedCornerShape(12.dp),       // Inner cards & panels
    extraLarge = RoundedCornerShape(14.dp)   // Main GlowCard containers
)
