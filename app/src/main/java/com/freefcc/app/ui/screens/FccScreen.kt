package com.freefcc.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freefcc.app.AppState
import com.freefcc.app.AutoFccMode
import com.freefcc.app.FccKeepaliveService
import com.freefcc.app.FccViewModel
import com.freefcc.app.UpdateInfo
import com.freefcc.app.ui.components.AutoModeToggle
import com.freefcc.app.ui.components.ConnectionStateHeader
import com.freefcc.app.ui.components.DroneSerialCard
import com.freefcc.app.ui.components.FccPowerRingGauge
import com.freefcc.app.ui.components.GlowButton
import com.freefcc.app.ui.components.GlowButtonSize
import com.freefcc.app.ui.components.GlowCard
import com.freefcc.app.ui.components.ModeBadge
import com.freefcc.app.ui.components.ProgressDisplay
import com.freefcc.app.ui.components.PulsingStatusDot
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

private val BottomNavHeight = 80.dp
private val PageHorizontalPadding = 16.dp
private val PageTopPadding = 8.dp
private val PageBottomPadding = 16.dp
private val SectionSpacing = 10.dp

/**
 * Milestone M3: Stateful entry composable for the main FCC control screen.
 * Collects [AppState] from [FccViewModel] via [collectAsStateWithLifecycle] and handles
 * system permission dialog launchers for Home Point accessibility and overlay button.
 */
@Composable
fun FccScreen(
    viewModel: FccViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Accessibility Permission Launcher for Auto FCC Home Point mode
    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (FccKeepaliveService.isDjiFlyTextAccessEnabled(context)) {
            viewModel.setAutoFccMode(AutoFccMode.HOME_POINT_TEXT, true)
        } else {
            Toast.makeText(
                context,
                "Abilita il servizio di accessibilità FreeFCC per l'Auto FCC Home Point",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val onRequestAccessibility: () -> Unit = {
        if (FccKeepaliveService.isDjiFlyTextAccessEnabled(context)) {
            viewModel.setAutoFccMode(AutoFccMode.HOME_POINT_TEXT, true)
        } else {
            Toast.makeText(
                context,
                "Attiva il servizio Accessibilità, poi torna in FreeFCC",
                Toast.LENGTH_LONG
            ).show()
            try {
                accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "Impostazioni di accessibilità non disponibili su questo radiocomando",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Overlay Permission Launcher for Floating Button
    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            viewModel.setFloatingButtonEnabled(true)
        } else {
            Toast.makeText(
                context,
                "Permesso 'Visualizzazione sopra altre app' richiesto per il Bottone Flottante",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val onRequestOverlay: (Boolean) -> Unit = { enabled: Boolean ->
        if (!enabled) {
            viewModel.setFloatingButtonEnabled(false)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            viewModel.setFloatingButtonEnabled(true)
        } else {
            Toast.makeText(
                context,
                "Concedi il permesso overlay per FreeFCC",
                Toast.LENGTH_LONG
            ).show()
            try {
                overlaySettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "Impostazioni overlay non disponibili su questo dispositivo",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    FccScreenContent(
        state = state,
        onConnectClick = { viewModel.connect() },
        onProbeSerial = { viewModel.probeSerial() },
        onSetManualSerial = { serial -> viewModel.setManualSerial(serial) },
        onEnableFcc = { viewModel.enableFcc() },
        onSetAutoFccMode = { mode, enabled -> viewModel.setAutoFccMode(mode, enabled) },
        onRequestAccessibility = onRequestAccessibility,
        onRequestOverlay = onRequestOverlay,
        onLaunchDjiFly = { viewModel.launchDjiFly() },
        onDownloadUpdate = { viewModel.downloadUpdate() },
        onInstallUpdate = { viewModel.installUpdate() },
        modifier = modifier
    )
}

/**
 * Convenient stateless overload accepting [AppState] directly.
 */
@Composable
fun FccScreen(
    state: AppState,
    onConnectClick: () -> Unit,
    onProbeSerial: () -> Unit,
    onSetManualSerial: (String) -> Unit,
    onEnableFcc: () -> Unit,
    onSetAutoFccMode: (AutoFccMode, Boolean) -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: (Boolean) -> Unit,
    onLaunchDjiFly: () -> Unit,
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    FccScreenContent(
        state = state,
        onConnectClick = onConnectClick,
        onProbeSerial = onProbeSerial,
        onSetManualSerial = onSetManualSerial,
        onEnableFcc = onEnableFcc,
        onSetAutoFccMode = onSetAutoFccMode,
        onRequestAccessibility = onRequestAccessibility,
        onRequestOverlay = onRequestOverlay,
        onLaunchDjiFly = onLaunchDjiFly,
        onDownloadUpdate = onDownloadUpdate,
        onInstallUpdate = onInstallUpdate,
        modifier = modifier
    )
}

/**
 * Pure stateless content composable for [FccScreen].
 * Decoupled from ViewModel for previews, testing, and clean architecture.
 */
@Composable
fun FccScreenContent(
    state: AppState,
    onConnectClick: () -> Unit,
    onProbeSerial: () -> Unit,
    onSetManualSerial: (String) -> Unit,
    onEnableFcc: () -> Unit,
    onSetAutoFccMode: (AutoFccMode, Boolean) -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: (Boolean) -> Unit,
    onLaunchDjiFly: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding)
            .padding(bottom = BottomNavHeight + PageBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(PageTopPadding))

        // 1. App Title Header Bar
        FccAppHeader(controllerModel = state.controllerModel)

        Spacer(Modifier.height(SectionSpacing))

        // 2. Update Available Banner (if update is pending)
        if (state.updateAvailable && state.updateInfo != null && !state.isCheckingUpdate) {
            FccUpdateAlertCard(
                updateInfo = state.updateInfo,
                isDownloading = state.isDownloadingUpdate,
                downloadProgress = state.updateDownloadProgress,
                isDownloaded = state.isUpdateDownloaded,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate
            )
            Spacer(Modifier.height(SectionSpacing))
        }

        // 3. Connection State Header Component (Milestone M2)
        ConnectionStateHeader(
            state = state,
            onConnectClick = onConnectClick
        )

        Spacer(Modifier.height(SectionSpacing))

        // 4. Drone Serial Card Component (Milestone M2)
        DroneSerialCard(
            state = state,
            onProbeSerial = onProbeSerial,
            onSetManualSerial = onSetManualSerial
        )

        Spacer(Modifier.height(SectionSpacing))

        // 5. FCC Power Ring & Status Action Card
        FccPowerControlCard(
            state = state,
            onEnableFcc = onEnableFcc
        )

        Spacer(Modifier.height(SectionSpacing))

        // 6. Auto FCC Modes & Floating Button Selection Card
        AutoFccModesCard(
            selectedAutoMode = state.selectedAutoMode,
            isFloatingButtonEnabled = state.isFloatingButtonEnabled,
            onSetAutoFccMode = onSetAutoFccMode,
            onRequestAccessibility = onRequestAccessibility,
            onRequestOverlay = onRequestOverlay
        )

        Spacer(Modifier.height(SectionSpacing))

        // 7. DJI Fly Quick Launch & System Status Card
        DjiFlyQuickLaunchCard(
            isFccEnabled = state.isFccEnabled,
            isConnected = state.isConnected,
            isHardwareBusy = state.isHardwareBusy,
            onLaunchDjiFly = onLaunchDjiFly
        )

        Spacer(Modifier.height(SectionSpacing))

        // 8. System Permissions & Diagnostics Status Card
        SystemPermissionsCard(
            onRequestAccessibility = onRequestAccessibility,
            onRequestOverlay = { onRequestOverlay(true) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Sub-Composables
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun FccAppHeader(
    controllerModel: String,
    modifier: Modifier = Modifier
) {
    val versionAndModel = if (controllerModel.isNotEmpty()) {
        "v${FccViewModel.APP_VERSION} · $controllerModel"
    } else {
        "v${FccViewModel.APP_VERSION}"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "FreeFCC Custom",
            color = BrandOrange,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.width(8.dp))
        StatusBadge(text = "🧪 BETA", color = Amber)
        Spacer(Modifier.weight(1f))
        Text(
            text = versionAndModel,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FccUpdateAlertCard(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Float,
    isDownloaded: Boolean,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlowCard(
        borderColor = StatusGreen,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Aggiornamento v${updateInfo.version}",
                        color = StatusGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isDownloaded -> "Pronto per l'installazione"
                            isDownloading -> "Download: ${(downloadProgress * 100).toInt()}%"
                            else -> "Nuova versione disponibile"
                        },
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (isDownloading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = StatusGreen,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                GlowButton(
                    text = if (isDownloaded) "INSTALLA" else "SCARICA",
                    color = StatusGreen,
                    onClick = if (isDownloaded) onInstallUpdate else onDownloadUpdate,
                    size = GlowButtonSize.COMPACT,
                    filled = true,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
        if (isDownloading && downloadProgress > 0f) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth(),
                color = StatusGreen,
                trackColor = DarkBorder
            )
        }
    }
}

@Composable
private fun FccPowerControlCard(
    state: AppState,
    onEnableFcc: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBorder = if (state.isFccEnabled) StatusGreen.copy(alpha = 0.5f) else BrandOrange.copy(alpha = 0.35f)

    GlowCard(
        borderColor = cardBorder,
        modifier = modifier
    ) {
        Text(
            text = "STATO TRASMISSIONE RF & POTENZA",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        // Power Gauge Ring Visualizer
        FccPowerRingGauge(isFccEnabled = state.isFccEnabled)

        Spacer(Modifier.height(8.dp))

        // Mode Status Badge ("FCC UNLOCKED" / "CE STANDARD")
        ModeBadge(
            isFccEnabled = state.isFccEnabled,
            badgeTitle = if (state.isFccEnabled) "FCC UNLOCKED" else "STANDARD CE",
            detail = if (state.isFccEnabled) "Potenza massima sbloccata (27-30 dBm)" else "Potenza limitata normativa CE (20 dBm)"
        )

        Spacer(Modifier.height(10.dp))

        // Progress display or status message
        if (state.isBusy) {
            ProgressDisplay(progress = state.busyProgress, label = state.message.ifEmpty { "Invio pacchetti FCC in corso..." })
        } else if (state.message.isNotEmpty()) {
            Surface(
                color = DarkBorder.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.message,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        } else {
            Text(
                text = "Premi il pulsante per inviare i comandi di sblocco FCC al radiocomando.",
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // Prominent FCC Enable Action Button
        GlowButton(
            text = if (state.isFccEnabled) "REINVIA RICHIESTA FCC" else "ABILITA MODALITÀ FCC",
            color = BrandOrange,
            filled = true,
            size = GlowButtonSize.LARGE,
            isLoading = state.isBusy,
            enabled = !state.isHardwareBusy && !state.isBusy,
            icon = Icons.Default.Bolt,
            onClick = onEnableFcc,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AutoFccModesCard(
    selectedAutoMode: AutoFccMode?,
    isFloatingButtonEnabled: Boolean,
    onSetAutoFccMode: (AutoFccMode, Boolean) -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: (Boolean) -> Unit,
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
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = BrandCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "MODALITÀ AUTOMATICHE & OVERLAY",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Auto FCC Home Point
            AutoModeToggle(
                text = "Auto FCC — Punto Home (Testo DJI Fly)",
                checked = selectedAutoMode == AutoFccMode.HOME_POINT_TEXT,
                onCheckedChange = { checked ->
                    if (checked) {
                        onRequestAccessibility()
                    } else {
                        onSetAutoFccMode(AutoFccMode.HOME_POINT_TEXT, false)
                    }
                }
            )

            // Auto FCC 5s periodic
            AutoModeToggle(
                text = "Auto FCC — Ricorrente ogni 5 secondi",
                checked = selectedAutoMode == AutoFccMode.PERIODIC_5S,
                onCheckedChange = { checked ->
                    onSetAutoFccMode(AutoFccMode.PERIODIC_5S, checked)
                }
            )

            // Floating Overlay Button
            AutoModeToggle(
                text = "Pulsante Flottante a Schermo (Overlay)",
                checked = isFloatingButtonEnabled,
                onCheckedChange = { checked ->
                    onRequestOverlay(checked)
                }
            )
        }
    }
}

@Composable
private fun DjiFlyQuickLaunchCard(
    isFccEnabled: Boolean,
    isConnected: Boolean,
    isHardwareBusy: Boolean,
    onLaunchDjiFly: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlowCard(
        borderColor = StatusGreen.copy(alpha = 0.35f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlightTakeoff,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AVVIO RAPIDO DJI FLY",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isFccEnabled) "FCC applicato — pronto per il volo" else "Avvia DJI Fly per verificare la modalità",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            GlowButton(
                text = "APRI DJI FLY",
                color = StatusGreen,
                filled = true,
                size = GlowButtonSize.DEFAULT,
                icon = Icons.AutoMirrored.Filled.Launch,
                onClick = onLaunchDjiFly,
                modifier = Modifier.width(135.dp)
            )
        }
    }
}

@Composable
private fun SystemPermissionsCard(
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessEnabled by remember { mutableStateOf(FccKeepaliveService.isDjiFlyTextAccessEnabled(context)) }
    var isOverlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessEnabled = FccKeepaliveService.isDjiFlyTextAccessEnabled(context)
                isOverlayEnabled = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    GlowCard(
        borderColor = DarkBorder,
        modifier = modifier
    ) {
        Text(
            text = "STATO SISTEMA & PERMESSI DI SISTEMA",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        // Accessibility Permission Item
        PermissionStatusRow(
            title = "Accessibilità (Testo Home Point)",
            isGranted = isAccessEnabled,
            onGrantClick = onRequestAccessibility
        )

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
        Spacer(Modifier.height(6.dp))

        // Overlay Permission Item
        PermissionStatusRow(
            title = "Permesso Overlay (Bottone Flottante)",
            isGranted = isOverlayEnabled,
            onGrantClick = onRequestOverlay
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            PulsingStatusDot(
                color = if (isGranted) StatusGreen else StatusRed,
                isPulsing = !isGranted,
                sizeDp = 8
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = if (isGranted) StatusGreen.copy(alpha = 0.15f) else BrandCyan.copy(alpha = 0.15f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if (isGranted) StatusGreen.copy(alpha = 0.5f) else BrandCyan.copy(alpha = 0.5f)),
            modifier = Modifier.clickable(enabled = !isGranted, onClick = onGrantClick)
        ) {
            Text(
                text = if (isGranted) "ATTIVO 🟢" else "ABILITA ↗",
                color = if (isGranted) StatusGreen else BrandCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Compose Previews
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun FccScreenPreviewConnectedFccOn() {
    FreeFccTheme {
        FccScreenContent(
            state = AppState(
                isConnected = true,
                controllerModel = "RM510",
                aircraftModelName = "DJI Air 3",
                aircraftModelCode = "WM161",
                aircraftSerial = "1581F4X123456789",
                isFccEnabled = true,
                message = "Region AU verified; FCC request written — verify RF mode in DJI Fly"
            ),
            onConnectClick = {},
            onProbeSerial = {},
            onSetManualSerial = {},
            onEnableFcc = {},
            onSetAutoFccMode = { _, _ -> },
            onRequestAccessibility = {},
            onRequestOverlay = {},
            onLaunchDjiFly = {},
            onDownloadUpdate = {},
            onInstallUpdate = {}
        )
    }
}

@Composable
fun FccScreenPreviewDisconnected() {
    FreeFccTheme {
        FccScreenContent(
            state = AppState(
                isConnected = false,
                controllerModel = "RM510",
                aircraftSerial = "",
                isFccEnabled = false,
                message = "Controller non trovato. Assicurati che il drone sia acceso e collegato."
            ),
            onConnectClick = {},
            onProbeSerial = {},
            onSetManualSerial = {},
            onEnableFcc = {},
            onSetAutoFccMode = { _, _ -> },
            onRequestAccessibility = {},
            onRequestOverlay = {},
            onLaunchDjiFly = {},
            onDownloadUpdate = {},
            onInstallUpdate = {}
        )
    }
}

@Composable
fun FccScreenPreviewBusyApplying() {
    FreeFccTheme {
        FccScreenContent(
            state = AppState(
                isConnected = true,
                controllerModel = "RM510",
                aircraftModelName = "DJI Mavic 3 Pro",
                aircraftModelCode = "WM260",
                aircraftSerial = "1581F4X11223344",
                isFccEnabled = false,
                isBusy = true,
                isHardwareBusy = true,
                busyProgress = 0.65f,
                message = "Invio pacchetti DUML FCC in corso (65%)..."
            ),
            onConnectClick = {},
            onProbeSerial = {},
            onSetManualSerial = {},
            onEnableFcc = {},
            onSetAutoFccMode = { _, _ -> },
            onRequestAccessibility = {},
            onRequestOverlay = {},
            onLaunchDjiFly = {},
            onDownloadUpdate = {},
            onInstallUpdate = {}
        )
    }
}

@Composable
fun FccScreenPreviewDisabledCE() {
    FreeFccTheme {
        FccScreenContent(
            state = AppState(
                isConnected = true,
                controllerModel = "RM510",
                aircraftModelName = "DJI Mini 4 Pro",
                aircraftModelCode = "WM162",
                aircraftSerial = "1581F4X987654321",
                isFccEnabled = false,
                message = "Stato di fabbrica CE (20 dBm)"
            ),
            onConnectClick = {},
            onProbeSerial = {},
            onSetManualSerial = {},
            onEnableFcc = {},
            onSetAutoFccMode = { _, _ -> },
            onRequestAccessibility = {},
            onRequestOverlay = {},
            onLaunchDjiFly = {},
            onDownloadUpdate = {},
            onInstallUpdate = {}
        )
    }
}

@Composable
fun FccScreenPreviewAutoFccActive() {
    FreeFccTheme {
        FccScreenContent(
            state = AppState(
                isConnected = true,
                controllerModel = "RM510",
                aircraftModelName = "DJI Avata 2",
                aircraftModelCode = "WM169",
                aircraftSerial = "1581F4X55667788",
                isFccEnabled = true,
                selectedAutoMode = AutoFccMode.PERIODIC_5S,
                isFloatingButtonEnabled = true,
                message = "Auto-FCC attivo (invio periodico ogni 5s)"
            ),
            onConnectClick = {},
            onProbeSerial = {},
            onSetManualSerial = {},
            onEnableFcc = {},
            onSetAutoFccMode = { _, _ -> },
            onRequestAccessibility = {},
            onRequestOverlay = {},
            onLaunchDjiFly = {},
            onDownloadUpdate = {},
            onInstallUpdate = {}
        )
    }
}
