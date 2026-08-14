package com.freefcc.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freefcc.app.AppState
import com.freefcc.app.FccViewModel
import com.freefcc.app.GpsState
import com.freefcc.app.LedState
import com.freefcc.app.ui.components.GlowCard
import com.freefcc.app.ui.components.StatusBadge
import com.freefcc.app.ui.theme.Amber
import com.freefcc.app.ui.theme.BrandCyan
import com.freefcc.app.ui.theme.BrandOrange
import com.freefcc.app.ui.theme.DarkBorder
import com.freefcc.app.ui.theme.StatusGreen
import com.freefcc.app.ui.theme.StatusRed
import com.freefcc.app.ui.theme.TextMuted
import com.freefcc.app.ui.theme.TextPrimary
import com.freefcc.app.ui.theme.TextSecondary

private val BottomNavHeight = 80.dp
private val PageHorizontalPadding = 16.dp
private val PageTopPadding = 8.dp
private val PageBottomPadding = 16.dp
private val SectionSpacing = 10.dp

@Composable
fun DiagnosticsScreen(
    viewModel: FccViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DiagnosticsScreenContent(
        state = state,
        onSetLanLoggingEnabled = { enabled -> viewModel.setLanLoggingEnabled(enabled) },
        onRefreshAircraftIdentity = { viewModel.refreshAircraftIdentity() },
        onSetGps = { enabled -> viewModel.setGps(enabled) },
        onRefreshGpsState = { viewModel.refreshGpsState() },
        onSetLed = { on -> viewModel.setLed(on) },
        onRefreshLedState = { viewModel.refreshLedState() },
        modifier = modifier
    )
}

@Composable
fun DiagnosticsScreenContent(
    state: AppState,
    onSetLanLoggingEnabled: (Boolean) -> Unit,
    onRefreshAircraftIdentity: () -> Unit,
    onSetGps: (Boolean) -> Unit,
    onRefreshGpsState: () -> Unit,
    onSetLed: (Boolean) -> Unit,
    onRefreshLedState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLogsCleared by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding)
            .padding(bottom = BottomNavHeight + PageBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(PageTopPadding))

        // 1. Diagnostics Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Info, null, tint = BrandCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "DIAGNOSTICA & LOG",
                color = BrandCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(text = "v${FccViewModel.APP_VERSION}", color = StatusGreen)
        }

        Spacer(Modifier.height(SectionSpacing))

        // 2. Technical Metadata Card
        GlowCard(borderColor = DarkBorder) {
            Text("Dettagli Tecnici Sistema", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            InfoRow("Versione App", FccViewModel.APP_VERSION)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = DarkBorder.copy(0.4f))
            InfoRow("Codice Radiocomando", state.controllerModel.ifEmpty { "Non rilevato" })
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = DarkBorder.copy(0.4f))
            InfoRow("Modello Drone", state.aircraftModelName.ifEmpty { state.aircraftModelCode.ifEmpty { "Non rilevato" } })
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = DarkBorder.copy(0.4f))
            InfoRow("Codice Drone", state.aircraftModelCode.ifEmpty { "Non rilevato" })
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = DarkBorder.copy(0.4f))
            InfoRow("S/N Drone", state.aircraftSerial.ifEmpty { "Non rilevato" })

            if (state.lanLogUrl.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = DarkBorder.copy(0.4f))
                InfoRow("LAN Bridge Endpoint", state.lanLogUrl, valueColor = BrandCyan)
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRefreshAircraftIdentity,
                enabled = !state.isHardwareBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan),
                border = BorderStroke(1.dp, BrandCyan.copy(0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aggiorna Identità Drone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(SectionSpacing))

        // 3. LAN Control Bridge Card
        GlowCard(borderColor = BrandCyan.copy(0.35f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Wifi, null, tint = BrandCyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("LAN Control Bridge", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Stato live e comandi via Wi-Fi privato", color = TextSecondary, fontSize = 11.sp)
                }
                if (state.isLanLogStarting) {
                    CircularProgressIndicator(color = BrandCyan, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Switch(
                        checked = state.lanLogUrl.isNotEmpty(),
                        onCheckedChange = onSetLanLoggingEnabled
                    )
                }
            }

            if (state.lanLogMessage.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    state.lanLogMessage,
                    color = if (state.lanLogMessage.contains("failed", ignoreCase = true)) StatusRed else TextSecondary,
                    fontSize = 11.sp
                )
            }

            if (state.lanLogUrl.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = Color(0xFF0F141C),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            state.lanLogUrl,
                            color = BrandCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(state.lanLogUrl)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copia URL", tint = BrandCyan, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(SectionSpacing))

        // 4. GPS Diagnostic Panel
        GlowCard(borderColor = DarkBorder) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GpsFixed, null, tint = BrandCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Diagnostica GPS Drone", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val gpsBadgeText = when (state.gpsState) {
                    GpsState.ON -> "GPS ATTIVO"
                    GpsState.OFF -> "GPS DISATTIVATO"
                    GpsState.UNEXPECTED -> "STATO INATTESO"
                    GpsState.UNKNOWN -> "NON VERIFICATO"
                }
                val gpsBadgeColor = when (state.gpsState) {
                    GpsState.ON -> StatusGreen
                    GpsState.OFF -> StatusRed
                    GpsState.UNEXPECTED -> Amber
                    GpsState.UNKNOWN -> TextMuted
                }
                StatusBadge(text = gpsBadgeText, color = gpsBadgeColor)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                state.gpsStatus.ifEmpty { "Stato GPS non ancora verificato." },
                color = TextSecondary,
                fontSize = 11.5.sp
            )

            Spacer(Modifier.height(10.dp))
            if (state.isGpsBusy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(color = BrandCyan, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Comando GPS in corso...", color = BrandCyan, fontSize = 12.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onSetGps(true) },
                        enabled = !state.isGpsBusy && !state.isHardwareBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color(0xFF0C0E11)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Attiva GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSetGps(false) },
                        enabled = !state.isGpsBusy && !state.isHardwareBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disattiva GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onRefreshGpsState,
                        enabled = !state.isGpsBusy && !state.isHardwareBusy,
                        border = BorderStroke(1.dp, BrandCyan.copy(0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Verifica", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(SectionSpacing))

        // 5. LED Diagnostic Panel
        GlowCard(borderColor = DarkBorder) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, null, tint = BrandCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Diagnostica LED Drone", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val ledBadgeText = when (state.ledState) {
                    LedState.ON -> "LED ACCESI"
                    LedState.OFF -> "LED SPENTI"
                    LedState.PARTIAL -> "PARZIALE"
                    LedState.UNKNOWN -> "NON VERIFICATO"
                }
                val ledBadgeColor = when (state.ledState) {
                    LedState.ON -> StatusGreen
                    LedState.OFF -> StatusRed
                    LedState.PARTIAL -> Amber
                    LedState.UNKNOWN -> TextMuted
                }
                StatusBadge(text = ledBadgeText, color = ledBadgeColor)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                state.ledStatus.ifEmpty { "Stato LED non ancora verificato." },
                color = TextSecondary,
                fontSize = 11.5.sp
            )

            Spacer(Modifier.height(10.dp))
            if (state.isLedBusy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(color = BrandCyan, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Comando LED in corso...", color = BrandCyan, fontSize = 12.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onSetLed(true) },
                        enabled = !state.isLedBusy && !state.isHardwareBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color(0xFF0C0E11)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accendi LED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSetLed(false) },
                        enabled = !state.isLedBusy && !state.isHardwareBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Spegni LED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onRefreshLedState,
                        enabled = !state.isLedBusy && !state.isHardwareBusy,
                        border = BorderStroke(1.dp, BrandCyan.copy(0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Verifica", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(SectionSpacing))

        // 6. Process Logs Viewer & Clear Log Actions Card
        GlowCard(borderColor = DarkBorder) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Log di Processo", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(
                        text = if (isLogsCleared) "0 voci" else "${state.logMessages.size} voci",
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Copy Logs Button
                    IconButton(
                        onClick = {
                            val logText = state.logMessages.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(logText))
                        },
                        enabled = state.logMessages.isNotEmpty() && !isLogsCleared,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copia Log", tint = BrandCyan, modifier = Modifier.size(18.dp))
                    }

                    // Clear Logs Button
                    IconButton(
                        onClick = { isLogsCleared = !isLogsCleared },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            "Pulisci Log",
                            tint = if (isLogsCleared) StatusGreen else BrandOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val displayedLogs = if (isLogsCleared) emptyList() else state.logMessages

            if (displayedLogs.isEmpty()) {
                Text(
                    if (isLogsCleared) "Log puliti nella vista UI. Tocca il cestino per ripristinare." else "Nessun messaggio di log disponibile.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Surface(
                    color = Color(0xFF0C0E11),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        displayedLogs.forEachIndexed { index, entry ->
                            val color = when {
                                entry.contains("enabled", true) || entry.contains("connected", true) ||
                                entry.contains("restored", true) || entry.contains("received", true) -> StatusGreen
                                entry.contains("fail", true) || entry.contains("error", true) -> StatusRed
                                entry.contains("Enabling", true) || entry.contains("Disabling", true) ||
                                entry.contains("Probing", true) || entry.contains("Querying", true) ||
                                entry.contains("Loaded", true) -> Amber
                                else -> BrandCyan.copy(0.7f)
                            }
                            if (index > 0) {
                                HorizontalDivider(color = DarkBorder.copy(0.2f), thickness = 0.5.dp)
                            }
                            Text(
                                entry,
                                color = color,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
