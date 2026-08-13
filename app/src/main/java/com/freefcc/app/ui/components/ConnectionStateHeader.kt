package com.freefcc.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefcc.app.AppState
import com.freefcc.app.ui.theme.BrandCyan
import com.freefcc.app.ui.theme.DarkBorder
import com.freefcc.app.ui.theme.FreeFccTheme
import com.freefcc.app.ui.theme.StatusGreen
import com.freefcc.app.ui.theme.StatusRed
import com.freefcc.app.ui.theme.TextMuted
import com.freefcc.app.ui.theme.TextPrimary
import com.freefcc.app.ui.theme.TextSecondary

/**
 * Milestone M2: Prominent Connection State Header component.
 * Displays controller connection status ("CONNESSO" / "DISCONNESSO") with an animated
 * [PulsingStatusDot], remote controller model, and aircraft model name/code.
 */
@Composable
fun ConnectionStateHeader(
    isConnected: Boolean,
    controllerModel: String,
    aircraftModelName: String,
    aircraftModelCode: String,
    modifier: Modifier = Modifier,
    statusMessage: String? = null,
    isHardwareBusy: Boolean = false,
    onConnectClick: (() -> Unit)? = null
) {
    val borderColor = if (isConnected) StatusGreen.copy(alpha = 0.4f) else StatusRed.copy(alpha = 0.35f)
    val statusColor = if (isConnected) StatusGreen else StatusRed
    val statusLabel = if (isConnected) "CONNESSO" else "DISCONNESSO"

    val formattedAircraftModel = when {
        aircraftModelName.isNotEmpty() && aircraftModelCode.isNotEmpty() -> "$aircraftModelName ($aircraftModelCode)"
        aircraftModelName.isNotEmpty() -> aircraftModelName
        aircraftModelCode.isNotEmpty() -> aircraftModelCode
        else -> "Non rilevato"
    }

    val formattedControllerModel = controllerModel.ifEmpty { "Non rilevato" }

    GlowCard(
        borderColor = borderColor,
        modifier = modifier
    ) {
        // --- Top Row: Status Dot + State Label + Action Button ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingStatusDot(
                    color = statusColor,
                    isPulsing = isConnected,
                    sizeDp = 10
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "STATO CONTROLLER",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            if (onConnectClick != null) {
                GlowButton(
                    text = if (isConnected) "RICONNETTI" else "CONNETTI",
                    color = BrandCyan,
                    onClick = onConnectClick,
                    filled = false,
                    isLoading = isHardwareBusy,
                    size = GlowButtonSize.COMPACT,
                    modifier = Modifier.width(110.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 1.dp)
        Spacer(Modifier.height(10.dp))

        // --- Detail Rows: Controller Model & Aircraft Model ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HeaderDetailRow(
                icon = Icons.Outlined.VideogameAsset,
                label = "Radiocomando",
                value = formattedControllerModel,
                isHighlight = controllerModel.isNotEmpty()
            )

            HeaderDetailRow(
                icon = Icons.Outlined.FlightTakeoff,
                label = "Modello Drone",
                value = formattedAircraftModel,
                isHighlight = aircraftModelName.isNotEmpty() || aircraftModelCode.isNotEmpty()
            )
        }

        if (!statusMessage.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = DarkBorder.copy(alpha = 0.25f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusMessage,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Convenient overload accepting [AppState] directly.
 */
@Composable
fun ConnectionStateHeader(
    state: AppState,
    modifier: Modifier = Modifier,
    onConnectClick: (() -> Unit)? = null
) {
    ConnectionStateHeader(
        isConnected = state.isConnected,
        controllerModel = state.controllerModel,
        aircraftModelName = state.aircraftModelName,
        aircraftModelCode = state.aircraftModelCode,
        modifier = modifier,
        statusMessage = state.message.takeIf { it.isNotEmpty() },
        isHardwareBusy = state.isHardwareBusy,
        onConnectClick = onConnectClick
    )
}

@Composable
private fun HeaderDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    isHighlight: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighlight) BrandCyan else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            color = if (isHighlight) TextPrimary else TextMuted,
            fontSize = 12.sp,
            fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun ConnectionStateHeaderConnectedPreview() {
    FreeFccTheme {
        ConnectionStateHeader(
            isConnected = true,
            controllerModel = "RM510",
            aircraftModelName = "DJI Air 3",
            aircraftModelCode = "WM161",
            statusMessage = "Connessione DUML attiva su porta 40007",
            onConnectClick = {}
        )
    }
}

@Composable
fun ConnectionStateHeaderDisconnectedPreview() {
    FreeFccTheme {
        ConnectionStateHeader(
            isConnected = false,
            controllerModel = "",
            aircraftModelName = "",
            aircraftModelCode = "",
            onConnectClick = {}
        )
    }
}
