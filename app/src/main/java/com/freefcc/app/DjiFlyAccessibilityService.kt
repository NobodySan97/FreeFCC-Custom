package com.freefcc.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal object DjiFlyHomePointMatcher {
    private val whitespace = Regex("\\s+")

    fun normalize(value: CharSequence): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()
            .trimEnd('.', '!', '?', '\u3002', '\uff01', '\uff1f')

    fun matches(value: CharSequence, phrases: Set<String>): Boolean =
        normalize(value) in phrases
}

private data class DjiFlyPhraseCatalog(
    val phrases: Set<String>,
    val localeCount: Int
)

internal object AircraftModelProbe {
    private const val CAPTURE_DURATION_MS = 1_500
    private const val CAPTURE_MAX_FRAMES = 128

    fun capture(preferredPort: Int? = null): AircraftModelIdentity? {
        val ports = buildList {
            preferredPort?.let(::add)
            addAll(DumlTransport.AIRCRAFT_IDENTITY_PORTS)
        }.distinct()

        var modelCode = ""
        var modelName = ""
        val transport = DumlTransport()
        for (port in ports) {
            var port40007Lease: Port40007Lock.Lease? = null
            var sessionLease: DumlPortSessionLock.Lease? = null
            try {
                if (port == DumlTransport.PORT_LED) {
                    port40007Lease = Port40007Lock.acquireForExternalBlocking(timeoutMs = 500)
                    if (port40007Lease == null) continue
                }
                sessionLease = DumlPortSessionLock.tryBegin(port) ?: continue
                val observed = DumlTransport.extractAircraftModelIdentity(
                    transport.captureFrames(
                        durationMs = CAPTURE_DURATION_MS,
                        maxFrames = CAPTURE_MAX_FRAMES,
                        port = port
                    )
                ) ?: continue
                if (observed.modelCode.isNotEmpty()) modelCode = observed.modelCode
                if (observed.modelName.isNotEmpty()) modelName = observed.modelName
                if (modelCode.isNotEmpty() && modelName.isNotEmpty()) break
            } finally {
                sessionLease?.close()
                port40007Lease?.let(Port40007Lock::releaseFromLed)
            }
        }

        return if (modelCode.isEmpty() && modelName.isEmpty()) {
            null
        } else {
            AircraftModelIdentity(modelCode, modelName)
        }
    }
}

/**
 * Reads text emitted by the original DJI Fly app. The accessibility service
 * never opens DUML itself; Home Point matches signal the continuously armed
 * Auto FCC service, which debounces duplicate UI events.
 */
class DjiFlyAccessibilityService : AccessibilityService() {

    companion object {
        private const val DJI_FLY_PACKAGE = "dji.go.v5"
        private const val DJI_PILOT_2_PACKAGE = "com.dji.industry.pilot"
        private val MODEL_CAPTURE_PACKAGES = setOf(DJI_FLY_PACKAGE, DJI_PILOT_2_PACKAGE)
        private const val MODEL_UI_REWRITE_MS = 60_000L
        private const val MODEL_HOME_POINT_DELAY_MS = 8_000L
        private const val MODEL_HARDWARE_IDLE_WAIT_MS = 15_000L
        private const val SERIAL_PROBE_INTERVAL_MS = 10_000L
        private const val SERIAL_PROBE_WINDOW_MS = 60_000L
        /** How long identity reads stand aside after a Home Point triggers FCC. */
        private const val IDENTITY_PROBE_FCC_GUARD_MS =
            AircraftIdentityProbePolicy.HOME_POINT_GUARD_MS
        /** Shortest gap between two windows opened by an alternating screen name. */
        private const val NAME_HINT_COOLDOWN_MS = 60_000L
        /** How long a `RUNNING` FCC attempt is believed before it is treated as stale. */
        private const val APPLY_RUNNING_TRUST_MS = 60_000L
        // Codes such as WM1615 and WM2605 end in a digit; without it here they
        // would pass for a serial instead of being rejected as a model code.
        private val AIRCRAFT_MODEL_CODE_PATTERN = Regex("^W[AM][0-9]{3}[0-9A-Z]?$")
        private val HOME_POINT_RESOURCE_NAMES = listOf(
            "fpv_tips_smart_rth_homepoint_update",
            "fpv_setting_shortcut_update_return_point_succeed_toast",
            "fpv_setting_safe_return_point_update_window_current_beacon_location_note",
            "fpv_setting_safe_return_point_update_window_current_control_location_note",
            "fpv_setting_safe_return_point_update_window_current_drone_location_note",
            "fpv_tips_target_location_lost_rth"
        )
    }

    private var homePointPhrases: Set<String> = emptySet()
    private var lastLoggedSignature = ""
    private var lastLoggedAtMs = 0L
    private var lastUiSnapshot = ""
    private var lastUiScanAtMs = 0L
    private var lastUiHomePointMatch = ""
    private var lastUiHomePointMatchAtMs = 0L
    private val modelCaptureBusy = AtomicBoolean(false)
    @Volatile private var codeProbeDoneForName = ""
    private val serialCaptureBusy = AtomicBoolean(false)
    @Volatile private var lastSerialProbeAtMs = 0L
    @Volatile private var pendingSwapName = ""
    @Volatile private var serialProbeWindowStartedAtMs = 0L
    @Volatile private var identityProbeBlockedUntilMs = 0L
    @Volatile private var homePointObservedAtMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val catalog = loadPhraseCatalog()
        homePointPhrases = catalog.phrases
        FccViewModel.logServiceEvent(
            "DJI FLY ACCESSIBILITY TEST: connected; " +
                "phrases=${catalog.phrases.size} locales=${catalog.localeCount}; " +
                "model read from the DJI app screen; ports stay closed until it names an aircraft"
        )
        if (AutoFccSelection.load(this) == AutoFccMode.HOME_POINT_TEXT) {
            FccKeepaliveService.startSelectedMode(this)
        }
        AppForegroundService.refresh(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val sourcePackage = event?.packageName?.toString() ?: return
        if (sourcePackage !in MODEL_CAPTURE_PACKAGES) return

        val values = buildSet {
            event.text.filterNotNull().forEach { if (it.isNotBlank()) add(it.toString()) }
            event.contentDescription?.takeIf { it.isNotBlank() }?.let { add(it.toString()) }
        }

        // Home Point first, always. An event can carry the Home Point text and a
        // model name at once, and reading identity from a port before the FCC
        // write would put diagnostics ahead of the one job that matters.
        if (sourcePackage != DJI_FLY_PACKAGE) {
            // The DJI app prints both the product code and the commercial name,
            // so its screen is the only trigger. Nothing is read from a port
            // until the screen proves an aircraft is linked.
            captureAircraftModelFromUi("event", values)
            return
        }
        logVisibleUiSnapshot()

        values.forEach { value ->
            val normalized = DjiFlyHomePointMatcher.normalize(value)
            if (normalized.isEmpty()) return@forEach
            val matched = DjiFlyHomePointMatcher.matches(value, homePointPhrases)
            val signature = "${event.eventType}:$normalized:$matched"
            val now = System.currentTimeMillis()
            if (signature == lastLoggedSignature && now - lastLoggedAtMs < 1_000L) return@forEach
            lastLoggedSignature = signature
            lastLoggedAtMs = now

            val safeText = value.replace(Regex("\\s+"), " ").take(240)
            FccViewModel.logServiceEvent(
                "DJI FLY ACCESSIBILITY EVENT: " +
                    "type=${AccessibilityEvent.eventTypeToString(event.eventType)} " +
                    "home_point_match=$matched text=$safeText"
            )
            if (matched) {
                handleHomePointMatch("event", value)
            }
        }
        captureAircraftModelFromUi("event", values)
    }

    /**
     * Reads the model straight off the DJI app screen. A trusted screen model
     * or a Home Point event can allow the bounded passive DUML fallback below;
     * neither path writes a command to the aircraft.
     */
    private fun captureAircraftModelFromUi(source: String, texts: Collection<String>): Boolean {
        if (texts.isEmpty()) return false
        val match = AircraftModelCatalog.findOnScreen(texts) ?: return false

        val prefs = getSharedPreferences("freefcc", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val storedCode = prefs.getString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, "").orEmpty()
        val storedName = prefs.getString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, "").orEmpty()
        val code = AircraftModelCatalog.codeFor(match.name, match.code, storedName, storedCode)
        val name = match.name.ifEmpty { storedName.takeIf { code == storedCode }.orEmpty() }
        val unchanged = code == storedCode && name == storedName
        // A confirmed swap to another aircraft invalidates the stored S/N: it
        // belongs to the previous one and would otherwise be reported next to
        // the new model. One sighting is not enough — DJI Fly screens list
        // other aircraft too, and a name flickering past would drop a serial
        // that was read correctly. Require the new name twice in a row.
        val nameChanged = storedName.isNotEmpty() && name.isNotEmpty() && name != storedName
        val aircraftSwapped = nameChanged && name == pendingSwapName
        pendingSwapName = if (nameChanged) name else ""
        // A changed name is only a hint: DJI Fly screens produce strays like
        // `DJI Lito X1 2021` and `DJI Lito`, and they alternate, so no name
        // ever repeats. The bus settles it — one serial, and it names the
        // aircraft outright. Let a single hint open the window that reads it,
        // but only one window per cooldown: alternating strays would otherwise
        // re-arm the window forever and turn it into a continuous port poll.
        if (
            nameChanged &&
            serialProbeWindowStartedAtMs == 0L &&
            now - prefs.getLong(FccViewModel.PREF_AIRCRAFT_BUS_READ_AT, 0L) >= NAME_HINT_COOLDOWN_MS
        ) {
            serialProbeWindowStartedAtMs = now
        }
        val lastWriteAt = prefs.getLong(FccViewModel.PREF_AIRCRAFT_MODEL_AT, 0L)
        val needsPassiveIdentity = code.isEmpty() || name.isEmpty()
        if (unchanged && now - lastWriteAt < MODEL_UI_REWRITE_MS) {
            if (needsPassiveIdentity) captureAircraftCodeFromDuml(name)
            return true
        }

        prefs.edit().apply {
            if (code.isEmpty()) {
                remove(FccViewModel.PREF_AIRCRAFT_MODEL_CODE)
            } else {
                putString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, code)
            }
            if (name.isEmpty()) {
                remove(FccViewModel.PREF_AIRCRAFT_MODEL_NAME)
            } else {
                putString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, name)
            }
            if (aircraftSwapped) {
                serialProbeWindowStartedAtMs = 0L
                AircraftSerialGuard.rememberDropped(
                    this,
                    prefs.getString(AircraftSerialGuard.KEY_SERIAL, "").orEmpty(),
                    now
                )
            }
            putString(
                FccViewModel.PREF_AIRCRAFT_MODEL_SOURCE,
                FccViewModel.AIRCRAFT_MODEL_SOURCE_UI
            )
            putLong(FccViewModel.PREF_AIRCRAFT_MODEL_AT, now)
        }.apply()
        val probeStarted = needsPassiveIdentity && captureAircraftCodeFromDuml(
            modelName = name,
            forceStatisticsAfterProbe = !unchanged
        )
        if (!unchanged && !probeStarted) {
            UsageStatistics.scheduleUpload(this, force = true)
        }
        if (!unchanged) {
            FccViewModel.logServiceEvent(
                    "Aircraft model read from the DJI app screen: source=$source " +
                        "code=${code.ifEmpty { "unknown" }} " +
                        "name=${name.ifEmpty { "unknown" }}"
            )
        }
        return true
    }

    /**
     * Reads the aircraft S/N while it is still unknown, so that an ordinary
     * session picks it up without the user pressing Connect or opening the Info
     * tab. The model name cannot serve as the trigger — DJI Fly does not always
     * name the aircraft, and the catalog does not know every one of them.
     *
     * The aircraft only broadcasts its serial once it is on the link, so this
     * retries on a slow beat while DJI Fly is on screen, then stops for good as
     * soon as the serial is known. One short bounded window per beat keeps port
     * 40007 essentially free — DJI Fly loses the aircraft when the port is held.
     */
    private fun captureAircraftSerialFromDuml(): Boolean {
        val prefs = getSharedPreferences("freefcc", Context.MODE_PRIVATE)
        val stored = prefs.getString(AircraftSerialGuard.KEY_SERIAL, "").orEmpty()
        val now = System.currentTimeMillis()
        if (!identityReadMayRun(now)) {
            // An open window must not burn down while it is being held back for
            // FCC: it would expire unread, and the Home Point that opened it
            // would be marked spent without the bus ever being asked.
            if (serialProbeWindowStartedAtMs != 0L) serialProbeWindowStartedAtMs = now
            return false
        }
        val lastBusReadAt = prefs.getLong(FccViewModel.PREF_AIRCRAFT_BUS_READ_AT, 0L)
        if (
            serialProbeWindowStartedAtMs == 0L &&
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = stored,
                homePointAtMs = homePointObservedAtMs,
                lastBusReadAtMs = lastBusReadAt,
                nowMs = now
            )
        ) {
            serialProbeWindowStartedAtMs = now
        }
        // Outside a window nothing is read: the screen hint, a missing S/N and
        // the slow re-check beat are the only things that open one.
        if (serialProbeWindowStartedAtMs == 0L) return false
        // An aircraft that does not put its serial on the bus never will —
        // Lito X1 only pushes it while the Information screen is open. Give up
        // after a minute instead of polling the port for the whole session,
        // and record the attempt so the beat restarts from here rather than
        // reopening a window on the next screen scan.
        if (now - serialProbeWindowStartedAtMs > SERIAL_PROBE_WINDOW_MS) {
            serialProbeWindowStartedAtMs = 0L
            prefs.edit().putLong(FccViewModel.PREF_AIRCRAFT_BUS_READ_AT, now).apply()
            return false
        }
        if (now - lastSerialProbeAtMs < SERIAL_PROBE_INTERVAL_MS) return false
        if (!serialCaptureBusy.compareAndSet(false, true)) return false
        lastSerialProbeAtMs = now

        thread(name = "FreeFCC-aircraft-serial", isDaemon = true) {
            var port40007Lease: Port40007Lock.Lease? = null
            var sessionLease: DumlPortSessionLock.Lease? = null
            try {
                if (HardwareLock.busy.value) return@thread
                port40007Lease = Port40007Lock.acquireForExternalBlocking(timeoutMs = 500)
                    ?: return@thread
                sessionLease = DumlPortSessionLock.tryBegin(DumlTransport.PORT_LED)
                    ?: return@thread
                val transport = DumlTransport()
                // Ask for the serial rather than wait for one to be broadcast.
                // Listening holds 40007 for the whole window and DJI Fly loses
                // the aircraft while it is held; the query returns as soon as
                // the aircraft answers. It also reaches aircraft that never put
                // the serial on the bus by themselves — Lito X1 only pushes it
                // while its Information screen is open, so a listening window
                // there burned a full minute for nothing.
                val queriedSerial = AircraftSerialQueryRunner.read(transport)
                // The listen is still the only place a model arrives over the
                // bus, so it runs when the model is missing even if the serial
                // is already in hand. With both known there is nothing left to
                // listen for and the port stays free.
                val modelKnown = prefs
                    .getString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, "")
                    .orEmpty()
                    .isNotEmpty()
                val listened = if (AircraftIdentitySources.needsListen(queriedSerial, modelKnown)) {
                    transport.probeAircraftLinkIdentity(
                        port = DumlTransport.PORT_LED,
                        attempts = 1
                    )
                } else {
                    null
                }
                val observed = AircraftIdentitySources.merge(queriedSerial, listened)
                val serial = observed.serial
                val observedAtMs = System.currentTimeMillis()
                if (serial.isNotEmpty() || observed.model != null) {
                    // The bus answered, so the identity is as fresh as it gets;
                    // the re-check beat starts over from this read.
                    prefs.edit()
                        .putLong(FccViewModel.PREF_AIRCRAFT_BUS_READ_AT, observedAtMs)
                        .apply()
                }
                val acceptedSerial = serial.takeIf {
                    it.isNotEmpty() &&
                        !AIRCRAFT_MODEL_CODE_PATTERN.matches(it) &&
                        it != stored &&
                        AircraftSerialGuard.accepts(prefs, it, observedAtMs)
                }
                val update = AircraftIdentityPreferences.updateFromDuml(
                    prefs = prefs,
                    observedModel = observed.model,
                    observedSerial = acceptedSerial,
                    nowMs = observedAtMs
                )
                if (update.modelChanged) {
                    FccViewModel.logServiceEvent(
                        "Aircraft model read on link: " +
                            "code=${update.currentModel.modelCode.ifEmpty { "unknown" }} " +
                            "name=${update.currentModel.modelName.ifEmpty { "unknown" }} " +
                            "source=${FccViewModel.AIRCRAFT_MODEL_SOURCE_DUML}"
                    )
                }
                if (update.serialChanged && update.currentSerial.isEmpty()) {
                    FccViewModel.logServiceEvent(
                        "Aircraft S/N cleared after model-code change: ${update.previousSerial}"
                    )
                }
                if (update.changed) {
                    UsageStatistics.scheduleUpload(this, force = true)
                }
                // A model code is not a serial; the aircraft may also still be
                // linking, so anything else just waits for the next beat.
                if (serial.isEmpty() || AIRCRAFT_MODEL_CODE_PATTERN.matches(serial)) return@thread
                if (acceptedSerial == null) {
                    if (serial == update.currentSerial) {
                        // Same aircraft — the screen name that opened this
                        // window was one of DJI Fly's stray labels.
                        serialProbeWindowStartedAtMs = 0L
                    }
                    return@thread
                }
                if (!update.serialChanged) {
                    // The UI may have stored the same S/N while this thread was
                    // waiting for the port leases.
                    serialProbeWindowStartedAtMs = 0L
                    return@thread
                }
                serialProbeWindowStartedAtMs = 0L
                FccViewModel.logServiceEvent(
                    if (update.previousSerial.isEmpty()) {
                        "Aircraft S/N read on link: ${update.currentSerial}"
                    } else {
                        "Aircraft S/N replaced from the bus: " +
                            "${update.previousSerial} -> ${update.currentSerial}"
                    }
                )
            } finally {
                sessionLease?.close()
                port40007Lease?.close()
                serialCaptureBusy.set(false)
            }
        }
        return true
    }

    /**
     * Resolves the product code for a model the screen named but did not code,
     * with one short passive window. Runs at most once per model name: with the
     * aircraft off the ports stay silent anyway, so there is nothing to retry.
     * No DUML command is written.
     */
    private fun captureAircraftCodeFromDuml(
        modelName: String,
        forceStatisticsAfterProbe: Boolean = false,
        delayBeforeProbeMs: Long = 0L
    ): Boolean {
        val probeKey = modelName.ifEmpty { "<connected-aircraft>" }
        if (probeKey == codeProbeDoneForName) return false
        if (!modelCaptureBusy.compareAndSet(false, true)) return false
        codeProbeDoneForName = probeKey
        val prefs = getSharedPreferences("freefcc", Context.MODE_PRIVATE)

        thread(name = "FreeFCC-aircraft-model", isDaemon = true) {
            var shouldForceStatistics = forceStatisticsAfterProbe
            try {
                if (delayBeforeProbeMs > 0L) Thread.sleep(delayBeforeProbeMs)
                val idleDeadline = System.currentTimeMillis() + MODEL_HARDWARE_IDLE_WAIT_MS
                while (
                    !identityReadMayRun(System.currentTimeMillis()) &&
                    System.currentTimeMillis() < idleDeadline
                ) {
                    Thread.sleep(250L)
                }
                if (!identityReadMayRun(System.currentTimeMillis())) {
                    codeProbeDoneForName = ""
                    FccViewModel.logServiceEvent(
                        "Aircraft code lookup deferred: FCC has the hardware"
                    )
                    return@thread
                }
                while (HardwareLock.busy.value && System.currentTimeMillis() < idleDeadline) {
                    Thread.sleep(250L)
                }
                if (HardwareLock.busy.value) {
                    codeProbeDoneForName = ""
                    FccViewModel.logServiceEvent(
                        "Aircraft code lookup deferred: hardware stayed busy"
                    )
                    return@thread
                }
                val identity = AircraftModelProbe.capture(
                    FccRuntime.tracker.state.value.controllerPort
                )
                if (identity == null) {
                    FccViewModel.logServiceEvent(
                        "Aircraft code lookup for $modelName: " +
                            "no 00:82/03:34 identity on known ports"
                    )
                    return@thread
                }

                val storedCode = prefs
                    .getString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, "")
                    .orEmpty()
                val storedName = prefs
                    .getString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, "")
                    .orEmpty()
                val candidateName = identity.modelName.ifEmpty { modelName }
                val resolvedCode = AircraftModelCatalog.codeFor(
                    name = candidateName,
                    observedCode = identity.modelCode,
                    storedName = storedName,
                    storedCode = storedCode
                )
                val resolvedName = candidateName.ifEmpty {
                    storedName.takeIf { resolvedCode == storedCode }.orEmpty()
                }
                shouldForceStatistics = shouldForceStatistics ||
                    resolvedCode != storedCode || resolvedName != storedName
                prefs.edit().apply {
                    if (resolvedCode.isEmpty()) {
                        remove(FccViewModel.PREF_AIRCRAFT_MODEL_CODE)
                    } else {
                        putString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, resolvedCode)
                    }
                    if (resolvedName.isEmpty()) {
                        remove(FccViewModel.PREF_AIRCRAFT_MODEL_NAME)
                    } else {
                        putString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, resolvedName)
                    }
                    putString(
                        FccViewModel.PREF_AIRCRAFT_MODEL_SOURCE,
                        FccViewModel.AIRCRAFT_MODEL_SOURCE_DUML
                    )
                    putLong(FccViewModel.PREF_AIRCRAFT_MODEL_AT, System.currentTimeMillis())
                }.apply()
                FccViewModel.logServiceEvent(
                    "Aircraft code lookup for $modelName: " +
                        "code=${resolvedCode.ifEmpty { "unknown" }} " +
                        "name=${resolvedName.ifEmpty { "unknown" }}"
                )
            } finally {
                modelCaptureBusy.set(false)
                if (shouldForceStatistics) {
                    UsageStatistics.scheduleUpload(this, force = true)
                }
            }
        }
        return true
    }

    private fun logVisibleUiSnapshot() {
        val now = System.currentTimeMillis()
        if (now - lastUiScanAtMs < 1_000L) return
        lastUiScanAtMs = now

        val root = rootInActiveWindow ?: return
        val labels = collectVisibleLabels(root)
        if (labels.isEmpty()) return

        val homePointText = labels.firstOrNull { value ->
            DjiFlyHomePointMatcher.matches(value, homePointPhrases)
        }
        val snapshot = labels.joinToString(" | ").take(1_500)
        val snapshotChanged = snapshot != lastUiSnapshot
        if (snapshotChanged) {
            lastUiSnapshot = snapshot
            FccViewModel.logServiceEvent(
                "DJI FLY ACCESSIBILITY UI: home_point_match=${homePointText != null} text=$snapshot"
            )
        }
        // Home Point is handled before anything else in the scan. It is what
        // triggers the FCC write, and the write needs port 40007: an identity
        // read started earlier in the same scan would hold that port and make
        // the write wait at the one moment the app exists for.
        if (snapshotChanged && homePointText != null) {
            val normalized = DjiFlyHomePointMatcher.normalize(homePointText)
            if (normalized != lastUiHomePointMatch || now - lastUiHomePointMatchAtMs >= 10_000L) {
                lastUiHomePointMatch = normalized
                lastUiHomePointMatchAtMs = now
                handleHomePointMatch("visible_ui", homePointText)
            }
        }

        UsageStatistics.captureControllerSerialFromUi(this, labels)
        // The Information screen is the sure source, the bus the opportunistic
        // one; whichever names the aircraft first wins.
        if (!UsageStatistics.captureAircraftSerialFromUi(this, labels)) {
            captureAircraftSerialFromDuml()
        }
        captureAircraftModelFromUi("visible_ui", labels)
    }

    /**
     * Identity is the lowest-priority thing this service does. It stands aside
     * for the guard after a Home Point, and for as long as an FCC attempt is
     * actually running — connect retries can outlast the guard, and the write
     * needs port 40007 for all of it.
     */
    private fun identityReadMayRun(nowMs: Long): Boolean {
        if (nowMs < identityProbeBlockedUntilMs) return false
        val attempt = FccRuntime.tracker.state.value.lastApplyAttempt ?: return true
        if (attempt.outcome != FccApplyOutcome.RUNNING) return true
        // A cancelled apply throws before it records a finish, so `RUNNING` can
        // outlive the attempt. Believing it forever would silence identity for
        // the rest of the session, so it is only believed while an apply could
        // plausibly still be running.
        return nowMs - attempt.startedAtMs > APPLY_RUNNING_TRUST_MS
    }

    private fun handleHomePointMatch(source: String, value: CharSequence) {
        // The FCC write starts now and runs on another thread.
        val matchedAtMs = System.currentTimeMillis()
        homePointObservedAtMs = matchedAtMs
        identityProbeBlockedUntilMs = matchedAtMs + IDENTITY_PROBE_FCC_GUARD_MS
        val accepted = FccKeepaliveService.notifyHomePointDetected()
        FccViewModel.logServiceEvent(
            "DJI FLY ACCESSIBILITY TEST: HOME POINT MATCH source=$source " +
                "auto_fcc_trigger_accepted=$accepted " +
                "text=${value.toString().replace(Regex("\\s+"), " ").take(240)}"
        )
        val prefs = getSharedPreferences("freefcc", Context.MODE_PRIVATE)
        if (
            prefs.getString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, "").isNullOrBlank() ||
            prefs.getString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, "").isNullOrBlank()
        ) {
            captureAircraftCodeFromDuml(
                modelName = prefs.getString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, "").orEmpty(),
                delayBeforeProbeMs = if (
                    accepted || AutoFccSelection.load(this) == AutoFccMode.HOME_POINT_TEXT
                ) {
                    MODEL_HOME_POINT_DELAY_MS
                } else {
                    0L
                }
            )
        }
    }

    private fun collectVisibleLabels(root: AccessibilityNodeInfo): Set<String> {
        val labels = linkedSetOf<String>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < 300 && labels.size < 80) {
            val node = pending.removeFirst()
            visited += 1
            node.text?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let(labels::add)
            node.contentDescription?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let(labels::add)
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return labels
    }

    override fun onInterrupt() {
        FccViewModel.logServiceEvent("DJI FLY ACCESSIBILITY TEST: interrupted")
    }

    @SuppressLint("AppBundleLocaleChanges", "DiscouragedApi")
    private fun loadPhraseCatalog(): DjiFlyPhraseCatalog {
        val packageContext = try {
            createPackageContext(DJI_FLY_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        } catch (e: Exception) {
            FccViewModel.logServiceEvent(
                "DJI FLY ACCESSIBILITY TEST: DJI Fly resources unavailable: ${e.message}"
            )
            return DjiFlyPhraseCatalog(emptySet(), 0)
        }

        val baseResources = packageContext.resources
        val localeTags = buildSet {
            baseResources.assets.locales
                .mapNotNull { it.takeIf(String::isNotBlank) }
                .forEach(::add)
            baseResources.configuration.locales.let { locales ->
                for (index in 0 until locales.size()) add(locales[index].toLanguageTag())
            }
            add(Locale.ENGLISH.toLanguageTag())
        }

        val phrases = buildSet {
            localeTags.forEach { languageTag ->
                val locale = Locale.forLanguageTag(languageTag)
                val configuration = Configuration(baseResources.configuration).apply {
                    setLocale(locale)
                }
                val localizedResources = packageContext
                    .createConfigurationContext(configuration)
                    .resources
                HOME_POINT_RESOURCE_NAMES.forEach { name ->
                    val id = localizedResources.getIdentifier(name, "string", DJI_FLY_PACKAGE)
                    if (id != 0) {
                        runCatching { localizedResources.getText(id) }
                            .getOrNull()
                            ?.let(DjiFlyHomePointMatcher::normalize)
                            ?.takeIf(String::isNotEmpty)
                            ?.let(::add)
                    }
                }
            }
        }
        return DjiFlyPhraseCatalog(phrases, localeTags.size)
    }
}
