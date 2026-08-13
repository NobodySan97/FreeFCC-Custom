package com.freefcc.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import com.freefcc.app.ui.theme.FreeFccTheme
import com.freefcc.app.ui.components.*

// ═══════════════════════════════════════════════════════════════════════
// Colors
// ═══════════════════════════════════════════════════════════════════════

private val BgDark = Color(0xFF0C0E11)
private val BgMid = Color(0xFF11151A)
private val BgLight = Color(0xFF1A2027)
private val CardBg = Color(0xFF151A20)
private val CardBorder = Color(0xFF303842)
private val Cyan = Color(0xFFFF9D4D)
private val Green = Color(0xFF4ED69A)
private val Amber = Color(0xFFFFD166)
private val Red = Color(0xFFFF5C70)
private val TextWhite = Color(0xFFF5F7FA)
private val TextGray = Color(0xFFA5AFBA)
private val TextDim = Color(0xFF687581)

private val BottomNavHeight = 34.dp
private val PageHorizontalPadding = 16.dp
private val PageTopPadding = 8.dp
private val PageBottomPadding = 12.dp
private val SectionSpacing = 8.dp

// ═══════════════════════════════════════════════════════════════════════
// Activity
// ═══════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {

    private val viewModel: FccViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) AppForegroundService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            AppForegroundService.start(this)
        }
        requestNotificationPermissionIfNeeded()
        requestBatteryExemptionOnce()
        viewModel.init()
        handleNotificationAction(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            FreeFccTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationAction(intent)
    }

    private fun handleNotificationAction(intent: Intent?) {
        if (intent?.action != AppForegroundService.ACTION_SELECT_HOME_POINT) return
        intent.action = null
        if (FccKeepaliveService.isDjiFlyTextAccessEnabled(this)) {
            viewModel.setAutoFccMode(AutoFccMode.HOME_POINT_TEXT, true)
            return
        }

        AutoFccSelection.save(this, AutoFccMode.HOME_POINT_TEXT)
        viewModel.refreshAutoFccSelection()
        AppForegroundService.refresh(this)
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Accessibility settings are unavailable on this controller",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Asks once to leave the battery optimization list. Auto FCC and the LAN
     * bridge run inside this process, so a doze kill stops them silently.
     * The prompt is never repeated: the user can still grant it later from
     * Android settings.
     */
    private fun requestBatteryExemptionOnce() {
        val prefs = getSharedPreferences("freefcc", MODE_PRIVATE)
        if (prefs.getBoolean("battery_exemption_asked", false)) return
        val power = getSystemService(PowerManager::class.java) ?: return
        if (power.isIgnoringBatteryOptimizations(packageName)) return

        prefs.edit().putBoolean("battery_exemption_asked", true).apply()
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Battery optimization settings are unavailable on this controller",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshAutoFccSelection()
        viewModel.refreshLanBridgeBinding()
    }

}

// ═══════════════════════════════════════════════════════════════════════
// Root layout
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun AppRoot(viewModel: FccViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val scope = rememberCoroutineScope()

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(700, easing = EaseOutCubic))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BgDark, BgMid, BgDark),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
            .alpha(entrance.value)
    ) {
        // Ambient glow — decorative only
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        listOf(Cyan.copy(0.05f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 600f
                    )
                )
        )

        // Page content — fills space above the bottom nav
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> FccPage(state, viewModel)
                1 -> ModemPage(state, viewModel)
                2 -> com.freefcc.app.ui.screens.DiagnosticsScreen(viewModel)
                3 -> com.freefcc.app.ui.screens.UpdateScreen(viewModel)
            }
        }

        // Bottom nav — fixed at the bottom, on top of everything
        BottomNavBar(
            currentPage = pagerState.currentPage,
            onPageSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            updateAvailable = state.updateAvailable,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Page 1: FCC
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun FccPage(state: AppState, viewModel: FccViewModel) {
    val updateInfo = state.updateInfo
    val context = LocalContext.current
    val startHomePointAuto = {
        viewModel.setAutoFccMode(AutoFccMode.HOME_POINT_TEXT, true)
    }
    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (FccKeepaliveService.isDjiFlyTextAccessEnabled(context)) {
            startHomePointAuto()
        } else {
            Toast.makeText(
                context,
                "Enable FreeFCC Custom Home Point Test to use text-based Auto FCC",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val requestHomePointAuto = {
        if (FccKeepaliveService.isDjiFlyTextAccessEnabled(context)) {
            startHomePointAuto()
        } else {
            Toast.makeText(
                context,
                "Enable FreeFCC Custom Home Point Test, then return to FreeFCC Custom",
                Toast.LENGTH_LONG
            ).show()
            try {
                accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "Accessibility settings are unavailable on this controller",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            viewModel.setFloatingButtonEnabled(true)
        } else {
            Toast.makeText(
                context,
                "Enable 'Display over other apps' permission to use Floating Button",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val requestFloatingButton = { enabled: Boolean ->
        if (!enabled) {
            viewModel.setFloatingButtonEnabled(false)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            viewModel.setFloatingButtonEnabled(true)
        } else {
            Toast.makeText(
                context,
                "Grant overlay permission for FreeFCC Custom",
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
                    "Overlay settings unavailable on this device",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding)
            .padding(bottom = BottomNavHeight + PageBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        FccHeader(state)

        // Update-available banner — shows on the FCC page so the user
        // doesn't have to manually check the Update tab.
        if (state.updateAvailable && updateInfo != null && !state.isCheckingUpdate) {
            Spacer(Modifier.height(SectionSpacing))
            GlowCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when {
                                state.isUpdateDownloaded -> viewModel.installUpdate()
                                !state.isDownloadingUpdate -> viewModel.downloadUpdate()
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Aggiornamento disponibile — v${updateInfo.version}",
                                color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when {
                                    state.isUpdateDownloaded -> "Tocca per installare l'aggiornamento ↗"
                                    state.isDownloadingUpdate -> "Scaricamento in corso... (${(state.updateDownloadProgress * 100).toInt()}%)"
                                    else -> "Tocca per scaricare ed installare ↗"
                                },
                                color = TextDim, fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        when {
                            state.isDownloadingUpdate -> {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Green,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        if (state.isUpdateDownloaded) {
                                            viewModel.installUpdate()
                                        } else {
                                            viewModel.downloadUpdate()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = BgDark),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        if (state.isUpdateDownloaded) "INSTALLA" else "AGGIORNA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    if (state.isDownloadingUpdate && state.updateDownloadProgress > 0f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.updateDownloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Green,
                            trackColor = TextGray.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        GlowCard {
            FccPowerRingGauge(state)
            Spacer(Modifier.height(6.dp))
            ModeBadge(state)
            Spacer(Modifier.height(6.dp))

            if (state.isBusy) {
                ProgressDisplay(state.busyProgress, state.message)
            } else if (state.message.isNotEmpty()) {
                BodyText(state.message)
            } else {
                BodyText("Scegli una modalità automatica o invia una richiesta FCC.")
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AutoModeToggle(
                        text = "Auto FCC — Punto Home",
                        checked = state.selectedAutoMode == AutoFccMode.HOME_POINT_TEXT,
                        onCheckedChange = { checked ->
                            if (checked) {
                                requestHomePointAuto()
                            } else {
                                viewModel.setAutoFccMode(AutoFccMode.HOME_POINT_TEXT, false)
                            }
                        }
                    )
                    AutoModeToggle(
                        text = "Auto FCC — ogni 5 sec",
                        checked = state.selectedAutoMode == AutoFccMode.PERIODIC_5S,
                        onCheckedChange = { checked ->
                            viewModel.setAutoFccMode(AutoFccMode.PERIODIC_5S, checked)
                        }
                    )
                    AutoModeToggle(
                        text = "Bottone Flottante — Overlay",
                        checked = state.isFloatingButtonEnabled,
                        onCheckedChange = { checked ->
                            requestFloatingButton(checked)
                        }
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlowButton(
                        "INVIA RICHIESTA FCC",
                        Color(0xFFFF9D4D), // Amber/Orange
                        filled = true,
                        enabled = !state.isHardwareBusy,
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        viewModel.enableFcc()
                    }
                    GlowButton(
                        "APRI DJI FLY",
                        Green,
                        filled = true,
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        viewModel.launchDjiFly()
                    }
                }
            }
        }

        Spacer(Modifier.height(SectionSpacing))
        SystemPermissionsCard(
            context = context,
            onRequestAccessibility = { requestHomePointAuto() },
            onRequestOverlay = { requestFloatingButton(true) }
        )

    }
}

@Composable
private fun SystemPermissionsCard(
    context: android.content.Context,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isAccessEnabled by remember { mutableStateOf(FccKeepaliveService.isDjiFlyTextAccessEnabled(context)) }
    var isOverlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessEnabled = FccKeepaliveService.isDjiFlyTextAccessEnabled(context)
                isOverlayEnabled = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    GlowCard {
        Text("Stato Sistema & Permessi", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PulsingStatusDot(color = if (isAccessEnabled) Green else Red, isPulsing = !isAccessEnabled)
                Text("Accessibilità (Home Point)", color = TextWhite, fontSize = 12.sp)
            }
            Text(
                if (isAccessEnabled) "ATTIVO 🟢" else "ATTIVA ↗",
                color = if (isAccessEnabled) Green else Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(!isAccessEnabled) { onRequestAccessibility() }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PulsingStatusDot(color = if (isOverlayEnabled) Green else Amber, isPulsing = !isOverlayEnabled)
                Text("Permesso Overlay Flottante", color = TextWhite, fontSize = 12.sp)
            }
            Text(
                if (isOverlayEnabled) "CONCESSO 🟢" else "CONCEDI ↗",
                color = if (isOverlayEnabled) Green else Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(!isOverlayEnabled) { onRequestOverlay() }
            )
        }
    }
}

@Composable
private fun GpsControlPanel(state: AppState, viewModel: FccViewModel, modifier: Modifier = Modifier) {
    val controlsEnabled = !state.isGpsBusy && !state.isLedBusy && !state.isHardwareBusy
    AircraftControlPanel(
        title = "Aircraft GPS",
        icon = Icons.Default.GpsFixed,
        stateText = state.gpsState.name,
        stateColor = when (state.gpsState) {
            GpsState.ON -> Green
            GpsState.OFF -> Red
            GpsState.UNEXPECTED -> Amber
            GpsState.UNKNOWN -> TextDim
        },
        status = state.gpsStatus,
        busy = state.isGpsBusy,
        refreshDescription = "Refresh GPS state",
        onRefresh = { viewModel.refreshGpsState() },
        refreshEnabled = controlsEnabled,
        modifier = modifier
    ) {
        CompactControlButton("GPS ON", Green, filled = true, enabled = controlsEnabled) {
            viewModel.setGps(true)
        }
        CompactControlButton("GPS OFF", Red, filled = false, enabled = controlsEnabled) {
            viewModel.setGps(false)
        }
    }
}

@Composable
private fun LedControlPanel(state: AppState, viewModel: FccViewModel, modifier: Modifier = Modifier) {
    val controlsEnabled = !state.isLedBusy && !state.isGpsBusy && !state.isHardwareBusy
    AircraftControlPanel(
        title = "Aircraft LEDs",
        icon = Icons.Default.Lightbulb,
        stateText = state.ledState.name,
        stateColor = when (state.ledState) {
            LedState.ON -> Green
            LedState.OFF -> TextGray
            LedState.PARTIAL -> Amber
            LedState.UNKNOWN -> TextDim
        },
        status = state.ledStatus,
        busy = state.isLedBusy,
        refreshDescription = "Refresh LED state",
        onRefresh = { viewModel.refreshLedState() },
        refreshEnabled = controlsEnabled,
        modifier = modifier
    ) {
        CompactControlButton("LED ON", Green, filled = true, enabled = controlsEnabled) {
            viewModel.setLed(true)
        }
        CompactControlButton("LED OFF", TextGray, filled = false, enabled = controlsEnabled) {
            viewModel.setLed(false)
        }
    }
}

@Composable
private fun AircraftControlPanel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    stateText: String,
    stateColor: Color,
    status: String,
    busy: Boolean,
    refreshDescription: String,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit
) {
    Surface(
        color = BgLight.copy(alpha = 0.72f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Cyan, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "State: $stateText",
                color = stateColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                status.ifEmpty { "Tap refresh to check" },
                color = TextGray,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 28.dp)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onRefresh,
                enabled = refreshEnabled,
                contentPadding = PaddingValues(horizontal = 6.dp),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, Cyan.copy(if (refreshEnabled) 0.6f else 0.2f)),
                modifier = Modifier.fillMaxWidth().height(34.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Cyan
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        refreshDescription,
                        tint = Cyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("REFRESH", color = Cyan, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = actions
            )
        }
    }
}

@Composable
private fun RowScope.CompactControlButton(
    text: String,
    color: Color,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) color else Color.Transparent,
            contentColor = if (filled) BgDark else color,
            disabledContainerColor = color.copy(0.14f),
            disabledContentColor = color.copy(0.35f)
        ),
        contentPadding = PaddingValues(horizontal = 3.dp),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, color.copy(if (enabled) 0.55f else 0.2f)),
        modifier = Modifier.weight(1f).height(36.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
    }
}
// ═══════════════════════════════════════════════════════════════════════
// Page 2: 4G modem
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ModemPage(state: AppState, viewModel: FccViewModel) {
    com.freefcc.app.ui.screens.ModemScreen(viewModel = viewModel)
}

// Legacy inline DiagnosticsPage and UpdatePage removed in M5 refactoring.

// ═══════════════════════════════════════════════════════════════════════
// Shared components
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun FccHeader(state: AppState) {
    val versionAndModel = if (state.controllerModel.isNotEmpty()) {
        "v${FccViewModel.APP_VERSION} · ${state.controllerModel}"
    } else {
        "v${FccViewModel.APP_VERSION}"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "FreeFCC Custom",
            color = Cyan,
            fontSize = 21.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Amber.copy(0.18f),
            border = BorderStroke(1.dp, Amber.copy(0.5f))
        ) {
            Text(
                "🧪 BETA",
                color = Amber,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            versionAndModel,
            color = TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PageTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Text(title, color = TextWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeBadge(state: AppState) {
    val active = state.isFccEnabled
    val presentation = fccUiPresentation(active)
    val bgBrush = if (active) {
        Brush.horizontalGradient(listOf(Color(0xFF2A1A10), Color(0xFF3A2113), Color(0xFF2A1A10)))
    } else {
        Brush.horizontalGradient(listOf(BgLight.copy(0.4f), BgLight.copy(0.2f)))
    }

    val checkScale = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            checkScale.snapTo(0f)
            checkScale.animateTo(1.2f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            checkScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else {
            checkScale.snapTo(0f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MODE",
                color = TextDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                presentation.badgeTitle,
                color = if (active) Amber else TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            if (active) {
                Icon(
                    Icons.Outlined.Info, null, tint = Amber,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        scaleX = checkScale.value
                        scaleY = checkScale.value
                    }
                )
            } else {
                Icon(
                    Icons.Outlined.Radio, null, tint = TextDim,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            presentation.detail,
            color = if (active) Amber.copy(0.8f) else TextGray,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ProgressDisplay(progress: Float, label: String) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BgLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeProgress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(Cyan, Green)))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${(safeProgress * 100).toInt()}%",
            color = TextGray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BodyText(text: String, color: Color = TextGray) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )
}

@Composable
private fun FccPowerRingGauge(state: AppState) {
    val active = state.isFccEnabled
    val ringColor = if (active) Green else Cyan
    val infiniteTransition = rememberInfiniteTransition(label = "powerRing")
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
        modifier = Modifier
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
                color = CardBorder.copy(0.4f),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )

            rotate(degrees = if (active) currentRotation else 0f, pivot = centerOffset) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            ringColor.copy(0.1f),
                            ringColor.copy(currentPulseAlpha),
                            ringColor.copy(0.2f),
                            ringColor.copy(currentPulseAlpha)
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = if (active) 280f else 180f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (active) Icons.Filled.Bolt else Icons.Filled.CellTower,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (active) "FCC ⚡" else "CE 🇪🇺",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                if (active) "27-30 dBm" else "20 dBm",
                color = ringColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SerialRow(serial: String, enabled: Boolean = true, onRefresh: () -> Unit) {
    val identityLabel = if (serial.startsWith("W")) "Model: " else "S/N: "
    val identityValue = serial.ifEmpty { "Not detected — tap refresh" }
    val isConnected = serial.isNotEmpty()

    val infiniteTransition = rememberInfiniteTransition(label = "droneFlight")
    val bobbingState = infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "droneBobbing"
    )

    Surface(
        color = BgLight.copy(0.4f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isConnected) Green.copy(0.4f) else CardBorder.copy(0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val offset = if (isConnected) bobbingState.value else 0f
                translationY = offset * density
            }
        ) {
                Icon(
                    Icons.Filled.Flight,
                    null,
                    tint = if (isConnected) Green else Cyan.copy(0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(identityLabel, color = TextGray, fontSize = 12.sp)
            Text(
                identityValue,
                color = if (isConnected) TextWhite else TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh, enabled = enabled, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Refresh, "Refresh", tint = TextGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = TextWhite) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextGray, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DividerLine(alpha: Float = 0.5f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CardBorder.copy(alpha))
    )
}

@Composable
private fun PulsingStatusDot(
    color: Color,
    sizeDp: Int = 8,
    isPulsing: Boolean = true
) {
    if (!isPulsing) {
        Box(modifier = Modifier.size(sizeDp.dp).background(color, CircleShape))
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size((sizeDp + 4).dp)) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha * 0.45f
                }
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .background(color, CircleShape)
        )
    }
}

@Composable
private fun GlowCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
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
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(CardBg, Color(0xFF11161D))
                ),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun AutoModeToggle(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = if (checked) Green.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            if (checked) 1.5.dp else 1.dp,
            if (checked) Green else CardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDark,
                    checkedTrackColor = Green,
                    checkedBorderColor = Green,
                    uncheckedThumbColor = TextGray,
                    uncheckedTrackColor = BgLight,
                    uncheckedBorderColor = CardBorder
                ),
                modifier = Modifier.scale(0.78f)
            )
            Text(
                text,
                color = if (checked) Green else TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun GlowButton(
    text: String,
    color: Color,
    filled: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth().height(46.dp),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) color else Color.Transparent,
            contentColor = if (filled) BgDark else color,
            disabledContainerColor = color.copy(0.2f),
            disabledContentColor = color.copy(0.4f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = when {
            !filled && enabled -> BorderStroke(1.5.dp, color.copy(0.6f))
            filled && enabled -> BorderStroke(1.dp, color.copy(0.3f))
            else -> null
        }
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.3.sp)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Bottom navigation bar
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BottomNavBar(
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    updateAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple("FCC", Icons.Filled.Wifi, 0),
        Triple("4G Modem", Icons.Filled.SettingsInputAntenna, 1),
        Triple("Diagnostica", Icons.Outlined.Info, 2),
        Triple("Update", Icons.Outlined.SystemUpdate, 3)
    )

    NavigationBar(
        containerColor = BgDark.copy(alpha = 0.98f),
        contentColor = TextWhite,
        tonalElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        tabs.forEach { (label, icon, index) ->
            val selected = currentPage == index
            NavigationBarItem(
                selected = selected,
                onClick = { onPageSelected(index) },
                icon = {
                    if (index == 3 && updateAvailable) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = Red)
                            }
                        ) {
                            Icon(icon, contentDescription = label)
                        }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = {
                    Text(
                        label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cyan,
                    selectedTextColor = Cyan,
                    indicatorColor = Cyan.copy(alpha = 0.18f),
                    unselectedIconColor = TextGray,
                    unselectedTextColor = TextGray
                )
            )
        }
    }
}
