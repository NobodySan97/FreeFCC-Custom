package com.freefcc.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freefcc.app.AppState
import com.freefcc.app.FccViewModel
import com.freefcc.app.ui.components.ConnectionStateHeader
import com.freefcc.app.ui.components.DroneSerialCard
import com.freefcc.app.ui.components.GlowButton
import com.freefcc.app.ui.components.GlowButtonSize
import com.freefcc.app.ui.components.GlowCard
import com.freefcc.app.ui.components.ProgressDisplay
import com.freefcc.app.ui.components.StatusBadge
import com.freefcc.app.ui.theme.Amber
import com.freefcc.app.ui.theme.BrandCyan
import com.freefcc.app.ui.theme.BrandOrange
import com.freefcc.app.ui.theme.DarkBorder
import com.freefcc.app.ui.theme.FreeFccTheme
import com.freefcc.app.ui.theme.StatusGreen
import com.freefcc.app.ui.theme.StatusRed
import com.freefcc.app.ui.theme.TextMuted
import com.freefcc.app.ui.theme.TextPrimary
import com.freefcc.app.ui.theme.TextSecondary


private val PageHorizontalPadding = 16.dp
private val PageTopPadding = 8.dp
private val PageBottomPadding = 16.dp
private val SectionSpacing = 10.dp

/**
 * Stateful entry point for the 4G Modem & Cellular configuration screen.
 * Collects [AppState] from [FccViewModel] via [collectAsStateWithLifecycle].
 */
@Composable
fun ModemScreen(
    viewModel: FccViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ModemScreenContent(
        state = state,
        onConnectClick = { viewModel.connect() },
        onProbeSerial = { viewModel.probeSerial() },
        onSetManualSerial = { serial -> viewModel.setManualSerial(serial) },
        onProbe4gEndpoint = { viewModel.probe4gEndpoint() },
        onSend4gActivationFrames = { viewModel.send4gActivationFrames() },
        modifier = modifier
    )
}

/**
 * Convenient stateless overload accepting [AppState] directly for previews and testing.
 */
@Composable
fun ModemScreen(
    state: AppState,
    onConnectClick: () -> Unit,
    onProbeSerial: () -> Unit,
    onSetManualSerial: (String) -> Unit,
    onProbe4gEndpoint: () -> Unit,
    onSend4gActivationFrames: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModemScreenContent(
        state = state,
        onConnectClick = onConnectClick,
        onProbeSerial = onProbeSerial,
        onSetManualSerial = onSetManualSerial,
        onProbe4gEndpoint = onProbe4gEndpoint,
        onSend4gActivationFrames = onSend4gActivationFrames,
        modifier = modifier
    )
}

/**
 * Pure stateless presentation composable for [ModemScreen].
 * Decoupled from ViewModel for previews, testing, and clean architecture.
 */
@Composable
fun ModemScreenContent(
    state: AppState,
    onConnectClick: () -> Unit,
    onProbeSerial: () -> Unit,
    onSetManualSerial: (String) -> Unit,
    onProbe4gEndpoint: () -> Unit,
    onSend4gActivationFrames: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding)
            .padding(bottom = PageBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(PageTopPadding))

        // 1. Header Bar
        ModemHeaderBar(controllerModel = state.controllerModel)

        Spacer(Modifier.height(SectionSpacing))

        // 2. Connection State Header Component
        ConnectionStateHeader(
            state = state,
            onConnectClick = onConnectClick
        )

        Spacer(Modifier.height(SectionSpacing))

        // 3. Drone Serial & Identity Override Card
        DroneSerialCard(
            state = state,
            onProbeSerial = onProbeSerial,
            onSetManualSerial = onSetManualSerial
        )

        Spacer(Modifier.height(SectionSpacing))

        // 4. 4G DUSS Endpoint Probe Card
        FourGEndpointProbeCard(
            state = state,
            onProbe4gEndpoint = onProbe4gEndpoint
        )

        Spacer(Modifier.height(SectionSpacing))

        // 5. 4G Activation & Progress Control Card
        FourGActivationCard(
            state = state,
            onSend4gActivationFrames = onSend4gActivationFrames
        )

        Spacer(Modifier.height(SectionSpacing))

        // 6. Full Untruncated 4G Message Card
        FourGMessageDisplayCard(
            fourGMessage = state.fourGMessage
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Sub-Composables
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ModemHeaderBar(
    controllerModel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SettingsInputAntenna,
            contentDescription = null,
            tint = BrandOrange,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "4G MODEM & CELLULAR",
            color = BrandOrange,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.width(8.dp))
        StatusBadge(text = "🧪 HYBRID 4G", color = Amber)
        Spacer(Modifier.weight(1f))
        if (controllerModel.isNotEmpty()) {
            Text(
                text = controllerModel,
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FourGEndpointProbeCard(
    state: AppState,
    onProbe4gEndpoint: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlowCard(
        borderColor = BrandCyan.copy(alpha = 0.35f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.SettingsInputAntenna,
                contentDescription = null,
                tint = BrandCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "4G DUSS ENDPOINT PROBE",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Verify local socket /duss/mb/0x205 reachability before sending activation frames.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(10.dp))

        GlowButton(
            text = "PROBE 4G ENDPOINT",
            color = BrandCyan,
            filled = false,
            size = GlowButtonSize.DEFAULT,
            icon = Icons.Default.Search,
            isLoading = state.is4gBusy,
            enabled = !state.isHardwareBusy && !state.is4gBusy,
            onClick = onProbe4gEndpoint,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FourGActivationCard(
    state: AppState,
    onSend4gActivationFrames: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBorder = if (state.is4gBusy) Amber.copy(alpha = 0.6f) else BrandOrange.copy(alpha = 0.35f)

    GlowCard(
        borderColor = cardBorder,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "4G ACTIVATION (HYBRID MODE)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            StatusBadge(text = "SERVICE 0x51:0x1A", color = Amber)
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Send targeted S/N frames to activate 4G LTE mod. Ensure cellular dongle is paired and active.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(12.dp))

        if (state.is4gBusy) {
            ProgressDisplay(
                progress = state.busyProgress,
                label = "Sending 4G frames in progress...",
                startColor = Amber,
                endColor = BrandCyan
            )
        } else {
            GlowButton(
                text = "SEND 4G ACTIVATION FRAMES",
                color = Amber,
                filled = true,
                size = GlowButtonSize.LARGE,
                icon = Icons.Default.Bolt,
                enabled = !state.isHardwareBusy && !state.is4gBusy,
                onClick = onSend4gActivationFrames,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FourGMessageDisplayCard(
    fourGMessage: String,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor) = when {
        fourGMessage.contains("0,0,0") || fourGMessage.contains("ACCEPTED") -> "ACCEPTED" to StatusGreen
        fourGMessage.contains("3,3,3") || fourGMessage.contains("REFUSED") -> "UNAVAILABLE" to StatusRed
        fourGMessage.contains("9,9,9") || fourGMessage.contains("invalid") -> "INVALID" to StatusRed
        fourGMessage.contains("unknown") || fourGMessage.contains("timeout") -> "UNKNOWN" to TextMuted
        fourGMessage.contains("No full aircraft serial") -> "MISSING REQUIREMENT" to StatusRed
        fourGMessage.contains("not reachable") -> "UNREACHABLE" to Amber
        fourGMessage.isNotEmpty() -> "RESPONSE RECEIVED" to BrandCyan
        else -> "READY" to TextMuted
    }

    GlowCard(
        borderColor = DarkBorder,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "4G STATUS & MESSAGES",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            StatusBadge(text = statusLabel, color = statusColor)
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 1.dp)
        Spacer(Modifier.height(8.dp))

        val displayText = fourGMessage.ifEmpty {
            "Targeted experimental 4G request (HYBRID mode). Endpoint reachability and successful write do not guarantee active 4G modem link if the cellular dongle is not armed."
        }

        Surface(
            color = Color(0xFF0F141C),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = displayText,
                    color = if (fourGMessage.isNotEmpty()) TextPrimary else TextSecondary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Compose Previews
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun ModemScreenPreviewConnected() {
    FreeFccTheme {
        ModemScreenContent(
            state = AppState(
                isConnected = true,
                controllerModel = "RM510",
                aircraftModelName = "DJI Air 3",
                aircraftModelCode = "WM161",
                aircraftSerial = "1581F4X123456789",
                is4gBusy = false,
                fourGMessage = "4G switch request ACCEPTED by the controller (resp 0,0,0) — the link switch is running; check 4G status on the aircraft."
            ),
            onConnectClick = {},
            onProbeSerial = {},
            onSetManualSerial = {},
            onProbe4gEndpoint = {},
            onSend4gActivationFrames = {}
        )
    }
}

@Composable
fun ModemScreenPreviewDisconnected() {
    FreeFccTheme {
        ModemScreenContent(
            state = AppState(
                isConnected = false,
                controllerModel = "RM510",
                aircraftSerial = "",
                is4gBusy = false,
                fourGMessage = ""
            ),
            onConnectClick = {},
            onProbeSerial = {},
            onSetManualSerial = {},
            onProbe4gEndpoint = {},
            onSend4gActivationFrames = {}
        )
    }
}
