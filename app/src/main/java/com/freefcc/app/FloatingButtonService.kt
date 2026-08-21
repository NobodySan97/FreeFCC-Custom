package com.freefcc.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Renders a semi-transparent floating overlay button and a quick mini menu over other applications (e.g. DJI Fly).
 * Features:
 * - Quick Auto FCC Home Point toggle
 * - Live Radio Country Status (AU vs CE)
 * - Dynamic status styling
 */
class FloatingButtonService : Service() {

    companion object {
        const val PREF_FLOATING_BUTTON_ENABLED = "floating_button_enabled"
        private const val PREF_FLOATING_BUTTON_X = "floating_button_x"
        private const val PREF_FLOATING_BUTTON_Y = "floating_button_y"

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences("freefcc", Context.MODE_PRIVATE)
                .getBoolean(PREF_FLOATING_BUTTON_ENABLED, false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("freefcc", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_FLOATING_BUTTON_ENABLED, enabled)
                .apply()
            if (enabled) {
                start(context)
            } else {
                stop(context)
            }
        }

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                return
            }
            if (!isEnabled(context)) return
            val intent = Intent(context, FloatingButtonService::class.java)
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingButtonService::class.java)
            runCatching { context.stopService(intent) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var floatingView: FrameLayout? = null
    private var floatingTextView: TextView? = null
    private var floatingBgDrawable: GradientDrawable? = null
    private var buttonParams: WindowManager.LayoutParams? = null

    private var miniMenuView: LinearLayout? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuShowing = false

    private var autoFccStatusTextView: TextView? = null
    private var radioStatusTextView: TextView? = null
    private var toggleAutoButton: Button? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        createFloatingView()
        createMiniMenuView()
        updateStatusDisplay()
    }

    private fun getOverlayType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun createFloatingView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val prefs = getSharedPreferences("freefcc", Context.MODE_PRIVATE)
        val initialX = prefs.getInt(PREF_FLOATING_BUTTON_X, 60)
        val initialY = prefs.getInt(PREF_FLOATING_BUTTON_Y, 180)

        val sizePx = dpToPx(54)

        buttonParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val frameLayout = FrameLayout(this)
        floatingBgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E6151A20"))
            setStroke(dpToPx(2), Color.parseColor("#FFFF9D4D"))
        }
        frameLayout.background = floatingBgDrawable
        frameLayout.elevation = dpToPx(6).toFloat()

        floatingTextView = TextView(this).apply {
            text = "FCC"
            setTextColor(Color.parseColor("#FFFF9D4D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        frameLayout.addView(floatingTextView, textParams)

        frameLayout.setOnTouchListener(object : View.OnTouchListener {
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var initialLayoutX = 0
            private var initialLayoutY = 0
            private var touchDownTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val p = buttonParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        initialLayoutX = p.x
                        initialLayoutY = p.y
                        touchDownTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        p.x = initialLayoutX + deltaX
                        p.y = initialLayoutY + deltaY
                        windowManager?.updateViewLayout(frameLayout, p)
                        if (isMenuShowing) {
                            repositionMenu()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = abs(event.rawX - initialTouchX)
                        val deltaY = abs(event.rawY - initialTouchY)
                        val duration = System.currentTimeMillis() - touchDownTime
                        if (deltaX < 15 && deltaY < 15 && duration < 350) {
                            toggleMiniMenu()
                        } else {
                            prefs.edit()
                                .putInt(PREF_FLOATING_BUTTON_X, p.x)
                                .putInt(PREF_FLOATING_BUTTON_Y, p.y)
                                .apply()
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingView = frameLayout
        runCatching {
            windowManager?.addView(floatingView, buttonParams)
        }.onFailure {
            stopSelf()
        }
    }

    private fun createMiniMenuView() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            padding(16, 14, 16, 14)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#F510151E"))
                setStroke(dpToPx(1), Color.parseColor("#404F5E"))
            }
            elevation = dpToPx(12).toFloat()
        }

        // Header Layout
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "⚡ FreeFCC Custom Control"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(titleView)

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#9EADB8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            padding(8, 0, 4, 0)
            setOnClickListener { hideMiniMenu() }
        }
        headerLayout.addView(closeBtn)

        container.addView(headerLayout)

        // Subtitle / Status Display
        autoFccStatusTextView = TextView(this).apply {
            text = "Auto FCC: DISATTIVATO"
            setTextColor(Color.parseColor("#9EADB8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dpToPx(6), 0, dpToPx(2))
        }
        container.addView(autoFccStatusTextView)

        radioStatusTextView = TextView(this).apply {
            text = "Stato Radio: Rilevamento in corso..."
            setTextColor(Color.parseColor("#7E8E9F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, 0, 0, dpToPx(10))
        }
        container.addView(radioStatusTextView)

        // Toggle Auto FCC Home Point Button
        toggleAutoButton = Button(this).apply {
            text = "⚡ Attiva Home Point Auto FCC"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable("#FF4CAF50")
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setOnClickListener { toggleAutoFccHomePoint() }
        }
        container.addView(toggleAutoButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dpToPx(8)) })

        // Restore CE Mode Button
        val ceRestoreBtn = Button(this).apply {
            text = "🇪🇺 Ripristina CE Mode (Standard)"
            setTextColor(Color.parseColor("#FFFF9D4D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = createButtonDrawable("#30241A")
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setOnClickListener { applyRegion("DE") }
        }
        container.addView(ceRestoreBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dpToPx(8)) })

        // Open Full App Button
        val openAppBtn = Button(this).apply {
            text = "🚀 Apri App Completa"
            setTextColor(Color.parseColor("#D0DDF0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = createButtonDrawable("#25303D")
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setOnClickListener {
                hideMiniMenu()
                openFullApp()
            }
        }
        container.addView(openAppBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        menuParams = WindowManager.LayoutParams(
            dpToPx(250),
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        miniMenuView = container
    }

    private fun toggleMiniMenu() {
        if (isMenuShowing) {
            hideMiniMenu()
        } else {
            showMiniMenu()
        }
    }

    private fun showMiniMenu() {
        val menu = miniMenuView ?: return
        repositionMenu()
        updateStatusDisplay()
        queryRadioState()
        if (!isMenuShowing) {
            runCatching {
                windowManager?.addView(menu, menuParams)
                isMenuShowing = true
            }
        }
    }

    private fun hideMiniMenu() {
        val menu = miniMenuView ?: return
        if (isMenuShowing) {
            runCatching {
                windowManager?.removeView(menu)
            }
            isMenuShowing = false
        }
    }

    private fun repositionMenu() {
        val bp = buttonParams ?: return
        val mp = menuParams ?: return
        mp.x = bp.x
        mp.y = bp.y + dpToPx(60)
        if (isMenuShowing) {
            runCatching { windowManager?.updateViewLayout(miniMenuView, mp) }
        }
    }

    private var lastVibratedCountry: String? = null

    private fun applyRegion(country: String) {
        scope.launch {
            val transport = DumlTransport()
            val port = transport.getDetectedPort().takeIf { it > 0 } ?: DumlTransport.PORT
            val hardwareLease = HardwareLock.tryBegin() ?: return@launch
            val sessionLease = DumlPortSessionLock.tryBegin(port)
            if (sessionLease == null) {
                hardwareLease.close()
                return@launch
            }
            val result = try {
                FccCountryRegion.ensure(transport, port, targetCountry = country)
            } finally {
                sessionLease.close()
                hardwareLease.close()
            }
            withContext(Dispatchers.Main) {
                if (result.observedCountry == "AU" && lastVibratedCountry != "AU") {
                    lastVibratedCountry = "AU"
                    FccHaptics.vibrateSuccess(this@FloatingButtonService)
                } else if (result.observedCountry != "AU") {
                    lastVibratedCountry = result.observedCountry
                }
                queryRadioState()
            }
        }
    }

    private fun queryRadioState() {
        scope.launch {
            val transport = DumlTransport()
            val port = transport.getDetectedPort().takeIf { it > 0 } ?: DumlTransport.PORT
            val hardwareLease = HardwareLock.tryBegin() ?: return@launch
            val sessionLease = DumlPortSessionLock.tryBegin(port)
            if (sessionLease == null) {
                hardwareLease.close()
                return@launch
            }
            val result = try {
                FccCountryRegion.ensure(transport, port)
            } finally {
                sessionLease.close()
                hardwareLease.close()
            }
            withContext(Dispatchers.Main) {
                if (result.observedCountry == "AU") {
                    if (lastVibratedCountry != "AU") {
                        lastVibratedCountry = "AU"
                        FccHaptics.vibrateSuccess(this@FloatingButtonService)
                    }
                    radioStatusTextView?.text = "Stato Radio: 🟢 AU (FCC ⚡)"
                    radioStatusTextView?.setTextColor(Color.parseColor("#4CAF50"))
                    floatingBgDrawable?.setStroke(dpToPx(2), Color.parseColor("#4CAF50"))
                    floatingTextView?.setTextColor(Color.parseColor("#4CAF50"))
                } else if (result.observedCountry != null) {
                    lastVibratedCountry = result.observedCountry
                    radioStatusTextView?.text = "Stato Radio: 🟠 ${result.observedCountry} (Standard)"
                    radioStatusTextView?.setTextColor(Color.parseColor("#FFFF9D4D"))
                    floatingBgDrawable?.setStroke(dpToPx(2), Color.parseColor("#FFFF9D4D"))
                    floatingTextView?.setTextColor(Color.parseColor("#FFFF9D4D"))
                } else {
                    radioStatusTextView?.text = "Stato Radio: ⚪ Inattivo / Non rilevato"
                    radioStatusTextView?.setTextColor(Color.parseColor("#7E8E9F"))
                    floatingBgDrawable?.setStroke(dpToPx(2), Color.parseColor("#7E8E9F"))
                    floatingTextView?.setTextColor(Color.parseColor("#7E8E9F"))
                }
                floatingView?.invalidate()
            }
        }
    }

    private fun toggleAutoFccHomePoint() {
        val currentMode = AutoFccSelection.load(this)
        if (currentMode == AutoFccMode.HOME_POINT_TEXT) {
            // Disable Auto FCC
            AutoFccSelection.save(this, null)
            FccKeepaliveService.stop(this)
            AppForegroundService.refresh(this)
        } else {
            // Enable Auto FCC Home Point Mode
            if (FccKeepaliveService.isDjiFlyTextAccessEnabled(this)) {
                AutoFccSelection.save(this, AutoFccMode.HOME_POINT_TEXT)
                FccKeepaliveService.start(this, AutoFccMode.HOME_POINT_TEXT)
                AppForegroundService.refresh(this)
            } else {
                // Prompt user to enable Accessibility
                hideMiniMenu()
                val openIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    action = AppForegroundService.ACTION_SELECT_HOME_POINT
                }
                startActivity(openIntent)
                return
            }
        }
        updateStatusDisplay()
    }

    private fun updateStatusDisplay() {
        val currentMode = AutoFccSelection.load(this)
        val isHomePointActive = (currentMode == AutoFccMode.HOME_POINT_TEXT)

        if (isHomePointActive) {
            floatingTextView?.text = "FCC ⚡"
            floatingTextView?.setTextColor(Color.parseColor("#4CAF50"))
            floatingBgDrawable?.setStroke(dpToPx(2), Color.parseColor("#4CAF50"))

            autoFccStatusTextView?.text = "Auto FCC: 🟢 ATTIVO (Home Point)"
            autoFccStatusTextView?.setTextColor(Color.parseColor("#4CAF50"))

            toggleAutoButton?.text = "⏹️ Disattiva Auto FCC"
            toggleAutoButton?.background = createButtonDrawable("#991F26")
        } else {
            floatingTextView?.text = "FCC"
            floatingTextView?.setTextColor(Color.parseColor("#FFFF9D4D"))
            floatingBgDrawable?.setStroke(dpToPx(2), Color.parseColor("#FFFF9D4D"))

            autoFccStatusTextView?.text = "Auto FCC: 🔴 DISATTIVATO"
            autoFccStatusTextView?.setTextColor(Color.parseColor("#9EADB8"))

            toggleAutoButton?.text = "⚡ Attiva Home Point Auto FCC"
            toggleAutoButton?.background = createButtonDrawable("#FF4CAF50")
        }
    }

    private fun openFullApp() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(openIntent)
    }

    private fun createButtonDrawable(colorHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(10).toFloat()
            setColor(Color.parseColor(colorHex))
        }
    }

    private fun View.padding(left: Int, top: Int, right: Int, bottom: Int) {
        setPadding(dpToPx(left), dpToPx(top), dpToPx(right), dpToPx(bottom))
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        hideMiniMenu()
        floatingView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        floatingView = null
        miniMenuView = null
    }
}
