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
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import com.freefcc.app.ui.components.SideNavigationRail
import com.freefcc.app.ui.screens.DiagnosticsScreen
import com.freefcc.app.ui.screens.FccScreen
import com.freefcc.app.ui.screens.ModemScreen
import com.freefcc.app.ui.screens.UpdateScreen
import com.freefcc.app.ui.theme.DarkBackground
import com.freefcc.app.ui.theme.DarkBackgroundMid
import com.freefcc.app.ui.theme.FreeFccTheme

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
                    listOf(DarkBackground, DarkBackgroundMid, DarkBackground)
                )
            )
            .graphicsLayer { alpha = entrance.value }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Side navigation
            SideNavigationRail(
                currentPage = pagerState.currentPage,
                onPageSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                updateAvailable = state.updateAvailable
            )

            // Page content — fills remaining space with dedicated modular screens
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().weight(1f),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> FccScreen(viewModel = viewModel)
                    1 -> ModemScreen(viewModel = viewModel)
                    2 -> DiagnosticsScreen(viewModel = viewModel)
                    3 -> UpdateScreen(viewModel = viewModel)
                }
            }
        }
    }
}
