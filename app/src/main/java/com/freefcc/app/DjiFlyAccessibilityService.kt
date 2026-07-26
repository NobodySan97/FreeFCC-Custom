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

        // The DJI app prints both the product code and the commercial name, so
        // its screen is the only trigger. Nothing is read from a port until the
        // screen proves an aircraft is linked.
        captureAircraftModelFromUi("event", values)
        if (sourcePackage != DJI_FLY_PACKAGE) return
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
    }

    /**
     * Reads the model straight off the DJI app screen, which prints both the
     * product code and the commercial name. This is also the only thing that
     * lets the DUML read below run: a name on screen is the proof that an
     * aircraft is powered on and linked.
     */
    private fun captureAircraftModelFromUi(source: String, texts: Collection<String>): Boolean {
        if (texts.isEmpty()) return false
        val match = AircraftModelCatalog.findInText(texts.joinToString(" | ")) ?: return false
        if (match.code.isEmpty() && match.name.isNotEmpty()) {
            captureAircraftCodeFromDuml(match.name)
        }

        val prefs = getSharedPreferences("freefcc", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val unchanged =
            match.code == prefs.getString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, "").orEmpty() &&
                match.name == prefs.getString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, "").orEmpty()
        val lastWriteAt = prefs.getLong(FccViewModel.PREF_AIRCRAFT_MODEL_AT, 0L)
        if (unchanged && now - lastWriteAt < MODEL_UI_REWRITE_MS) return true

        prefs.edit().apply {
            if (match.code.isNotEmpty()) {
                putString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, match.code)
            }
            if (match.name.isNotEmpty()) {
                putString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, match.name)
            }
            putString(
                FccViewModel.PREF_AIRCRAFT_MODEL_SOURCE,
                FccViewModel.AIRCRAFT_MODEL_SOURCE_UI
            )
            putLong(FccViewModel.PREF_AIRCRAFT_MODEL_AT, now)
        }.apply()
        if (!unchanged) {
            FccViewModel.logServiceEvent(
                "Aircraft model read from the DJI app screen: source=$source " +
                    "code=${match.code.ifEmpty { "unknown" }} " +
                    "name=${match.name.ifEmpty { "unknown" }}"
            )
        }
        return true
    }

    /**
     * Resolves the product code for a model the screen named but did not code,
     * with one short passive window. Runs at most once per model name: with the
     * aircraft off the ports stay silent anyway, so there is nothing to retry.
     * No DUML command is written.
     */
    private fun captureAircraftCodeFromDuml(modelName: String) {
        if (modelName == codeProbeDoneForName) return
        if (!modelCaptureBusy.compareAndSet(false, true)) return
        codeProbeDoneForName = modelName
        val prefs = getSharedPreferences("freefcc", Context.MODE_PRIVATE)

        thread(name = "FreeFCC-aircraft-model", isDaemon = true) {
            try {
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

                prefs.edit().apply {
                    if (identity.modelCode.isNotEmpty()) {
                        putString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, identity.modelCode)
                    }
                    if (identity.modelName.isNotEmpty()) {
                        putString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, identity.modelName)
                    }
                    putString(
                        FccViewModel.PREF_AIRCRAFT_MODEL_SOURCE,
                        FccViewModel.AIRCRAFT_MODEL_SOURCE_DUML
                    )
                    putLong(FccViewModel.PREF_AIRCRAFT_MODEL_AT, System.currentTimeMillis())
                }.apply()
                FccViewModel.logServiceEvent(
                    "Aircraft code lookup for $modelName: " +
                        "code=${identity.modelCode.ifEmpty { "unknown" }} " +
                        "name=${identity.modelName.ifEmpty { "unknown" }}"
                )
            } finally {
                modelCaptureBusy.set(false)
            }
        }
    }

    private fun logVisibleUiSnapshot() {
        val now = System.currentTimeMillis()
        if (now - lastUiScanAtMs < 1_000L) return
        lastUiScanAtMs = now

        val root = rootInActiveWindow ?: return
        val labels = collectVisibleLabels(root)
        if (labels.isEmpty()) return
        captureAircraftModelFromUi("visible_ui", labels)
        val homePointText = labels.firstOrNull { value ->
            DjiFlyHomePointMatcher.matches(value, homePointPhrases)
        }
        val snapshot = labels.joinToString(" | ").take(1_500)
        if (snapshot == lastUiSnapshot) return
        lastUiSnapshot = snapshot
        FccViewModel.logServiceEvent(
            "DJI FLY ACCESSIBILITY UI: home_point_match=${homePointText != null} text=$snapshot"
        )
        if (homePointText != null) {
            val normalized = DjiFlyHomePointMatcher.normalize(homePointText)
            if (normalized != lastUiHomePointMatch || now - lastUiHomePointMatchAtMs >= 10_000L) {
                lastUiHomePointMatch = normalized
                lastUiHomePointMatchAtMs = now
                handleHomePointMatch("visible_ui", homePointText)
            }
        }
    }

    private fun handleHomePointMatch(source: String, value: CharSequence) {
        val accepted = FccKeepaliveService.notifyHomePointDetected()
        FccViewModel.logServiceEvent(
            "DJI FLY ACCESSIBILITY TEST: HOME POINT MATCH source=$source " +
                "auto_fcc_trigger_accepted=$accepted " +
                "text=${value.toString().replace(Regex("\\s+"), " ").take(240)}"
        )
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
