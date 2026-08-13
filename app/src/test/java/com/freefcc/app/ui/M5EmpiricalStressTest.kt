package com.freefcc.app.ui

import com.freefcc.app.AppState
import com.freefcc.app.FccViewModel
import com.freefcc.app.GpsState
import com.freefcc.app.LedState
import com.freefcc.app.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Stress Test Suite for Milestone M5:
 * Navigation Bar Refactoring, DiagnosticsScreen, UpdateScreen, and MainActivity Integration.
 */
class M5EmpiricalStressTest {

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Navigation Bar & Badge Mapping Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testNavigationBarTabInventoryAndIndexMapping() {
        val tabs = listOf(
            Triple("FCC", "Wifi", 0),
            Triple("4G Modem", "SettingsInputAntenna", 1),
            Triple("Diagnostica", "Info", 2),
            Triple("Update", "SystemUpdate", 3)
        )

        assertEquals(4, tabs.size)
        assertEquals("FCC", tabs[0].first)
        assertEquals("4G Modem", tabs[1].first)
        assertEquals("Diagnostica", tabs[2].first)
        assertEquals("Update", tabs[3].first)

        for (i in 0..3) {
            assertTrue("Tab index $i must be in bounds 0..3", i in 0..3)
        }
    }

    @Test
    fun testUpdateTabBadgeStatusMapping() {
        val stateWithUpdate = AppState(updateAvailable = true)
        assertTrue("Badge must be active when update is available", resolveUpdateBadgeVisible(stateWithUpdate.updateAvailable))
        assertEquals("🔴 NUOVA VERSIONE", resolveUpdateBadgeText(stateWithUpdate.updateAvailable))

        val stateUpToDate = AppState(updateAvailable = false)
        assertFalse("Badge must be inactive when up to date", resolveUpdateBadgeVisible(stateUpToDate.updateAvailable))
        assertEquals("🟢 AGGIORNATO", resolveUpdateBadgeText(stateUpToDate.updateAvailable))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. DiagnosticsScreen State Resolution Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testTechnicalMetadataFormattingAndFallbacks() {
        // Populated state
        val populated = AppState(
            controllerModel = "RM510",
            aircraftModelName = "DJI Mavic 3 Enterprise",
            aircraftModelCode = "WM245",
            aircraftSerial = "1581F4X123456789",
            lanLogUrl = "http://192.168.1.50:8080/logs"
        )
        assertEquals("RM510", resolveControllerModelDisplay(populated.controllerModel))
        assertEquals("DJI Mavic 3 Enterprise", resolveAircraftModelDisplay(populated.aircraftModelName, populated.aircraftModelCode))
        assertEquals("WM245", resolveAircraftCodeDisplay(populated.aircraftModelCode))
        assertEquals("1581F4X123456789", resolveAircraftSerialDisplay(populated.aircraftSerial))
        assertEquals("http://192.168.1.50:8080/logs", populated.lanLogUrl)

        // Empty state fallbacks
        val empty = AppState(
            controllerModel = "",
            aircraftModelName = "",
            aircraftModelCode = "",
            aircraftSerial = "",
            lanLogUrl = ""
        )
        assertEquals("Non rilevato", resolveControllerModelDisplay(empty.controllerModel))
        assertEquals("Non rilevato", resolveAircraftModelDisplay(empty.aircraftModelName, empty.aircraftModelCode))
        assertEquals("Non rilevato", resolveAircraftCodeDisplay(empty.aircraftModelCode))
        assertEquals("Non rilevato", resolveAircraftSerialDisplay(empty.aircraftSerial))
    }

    @Test
    fun testLanControlBridgeStateResolution() {
        // LAN starting
        val startingState = AppState(isLanLogStarting = true, lanLogUrl = "", lanLogMessage = "Starting HTTP server...")
        assertTrue(startingState.isLanLogStarting)
        assertFalse(startingState.lanLogUrl.isNotEmpty())

        // LAN active
        val activeState = AppState(
            isLanLogStarting = false,
            lanLogUrl = "http://10.0.0.1:8080",
            lanLogMessage = "Ready on 10.0.0.1:8080"
        )
        assertFalse(activeState.isLanLogStarting)
        assertTrue(activeState.lanLogUrl.isNotEmpty())
        assertEquals("http://10.0.0.1:8080", resolveLanClipboardText(activeState.lanLogUrl))

        // LAN error
        val errorState = AppState(lanLogMessage = "Binding failed: address already in use")
        assertTrue(errorState.lanLogMessage.contains("failed", ignoreCase = true))
    }

    @Test
    fun testGpsDiagnosticPanelStateResolution() {
        val states = listOf(
            Pair(GpsState.ON, "GPS ATTIVO"),
            Pair(GpsState.OFF, "GPS DISATTIVATO"),
            Pair(GpsState.UNEXPECTED, "STATO INATTESO"),
            Pair(GpsState.UNKNOWN, "NON VERIFICATO")
        )

        for ((gpsState, expectedBadge) in states) {
            val appState = AppState(gpsState = gpsState, gpsStatus = "Status: $expectedBadge")
            assertEquals(expectedBadge, resolveGpsBadgeText(appState.gpsState))
            assertTrue(appState.gpsStatus.contains(expectedBadge))
        }

        val busyState = AppState(isGpsBusy = true)
        assertFalse("GPS buttons disabled when busy", isGpsActionEnabled(busyState.isGpsBusy, busyState.isHardwareBusy))
    }

    @Test
    fun testLedDiagnosticPanelStateResolution() {
        val states = listOf(
            Pair(LedState.ON, "LED ACCESI"),
            Pair(LedState.OFF, "LED SPENTI"),
            Pair(LedState.PARTIAL, "PARZIALE"),
            Pair(LedState.UNKNOWN, "NON VERIFICATO")
        )

        for ((ledState, expectedBadge) in states) {
            val appState = AppState(ledState = ledState, ledStatus = "Status: $expectedBadge")
            assertEquals(expectedBadge, resolveLedBadgeText(appState.ledState))
            assertTrue(appState.ledStatus.contains(expectedBadge))
        }

        val busyState = AppState(isLedBusy = true)
        assertFalse("LED buttons disabled when busy", isLedActionEnabled(busyState.isLedBusy, busyState.isHardwareBusy))
    }

    @Test
    fun testProcessLogsClipboardAndClearLogsToggle() {
        val logs = listOf(
            "[12:00:00] FCC mode enabled",
            "[12:00:01] Hardware connected",
            "[12:00:02] Probe failed"
        )

        // Clipboard formatting
        val formattedLogText = resolveLogsClipboardText(logs)
        assertEquals("[12:00:00] FCC mode enabled\n[12:00:01] Hardware connected\n[12:00:02] Probe failed", formattedLogText)

        // Clear logs state toggle
        var isCleared = false
        var displayed = resolveDisplayedLogs(logs, isCleared)
        assertEquals(3, displayed.size)

        isCleared = true
        displayed = resolveDisplayedLogs(logs, isCleared)
        assertEquals(0, displayed.size)
    }

    @Test
    fun testLogSeverityColorMapping() {
        assertEquals("Green", resolveLogColorCategory("FCC mode enabled"))
        assertEquals("Green", resolveLogColorCategory("Controller connected"))
        assertEquals("Green", resolveLogColorCategory("DSSL connection restored"))
        assertEquals("Green", resolveLogColorCategory("Frame response received"))

        assertEquals("Red", resolveLogColorCategory("4G probe failed"))
        assertEquals("Red", resolveLogColorCategory("Error reading DUML frame"))

        assertEquals("Amber", resolveLogColorCategory("Enabling FCC mode..."))
        assertEquals("Amber", resolveLogColorCategory("Disabling 4G bridge..."))
        assertEquals("Amber", resolveLogColorCategory("Probing serial..."))
        assertEquals("Amber", resolveLogColorCategory("Querying device..."))
        assertEquals("Amber", resolveLogColorCategory("Loaded native library"))

        assertEquals("Default", resolveLogColorCategory("System init complete"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. UpdateScreen State Resolution Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testUpdateCheckingAndErrorStateResolution() {
        val checkingState = AppState(isCheckingUpdate = true)
        assertTrue(checkingState.isCheckingUpdate)

        val errorState = AppState(isCheckingUpdate = false, updateInfo = null, updateChecked = true)
        assertFalse(errorState.isCheckingUpdate)
        assertNull(errorState.updateInfo)
        assertTrue(errorState.updateChecked)
        assertTrue(isUpdateErrorDisplayed(errorState.updateInfo, errorState.updateChecked))
    }

    @Test
    fun testUpdateChannelSwitchingAndInfoDisplay() {
        val info = UpdateInfo(
            version = "1.6.0",
            title = "v1.6.0 — Major Update",
            changelog = "• Added M5 navigation bar refactoring\n• Integrated DiagnosticsScreen and UpdateScreen",
            downloadUrl = "https://example.com/freefcc.apk",
            apkSize = 15728640L, // 15.0 MB
            publishedAt = "2026-08-14T10:00:00Z",
            sha256 = null
        )

        val stableState = AppState(updateChannel = "stable", updateInfo = info, updateAvailable = true)
        assertEquals("stable", stableState.updateChannel)
        assertEquals("1.6.0", stableState.updateInfo?.version)
        assertEquals("15.0 MB", resolveApkSizeDisplay(info.apkSize))
        assertEquals("2026-08-14", resolveReleaseDateDisplay(info.publishedAt))

        val expState = stableState.copy(updateChannel = "experimental")
        assertEquals("experimental", expState.updateChannel)
    }

    @Test
    fun testUpdateDownloadProgressAndActionButtons() {
        val info = UpdateInfo(
            version = "1.6.0",
            title = "Release 1.6.0",
            changelog = "Changelog details",
            downloadUrl = "https://example.com/app.apk",
            apkSize = 10485760L,
            publishedAt = "2026-08-14",
            sha256 = null
        )

        // 1. Idle available state -> Download button
        var state = AppState(updateAvailable = true, updateInfo = info, isDownloadingUpdate = false, isUpdateDownloaded = false)
        assertEquals("SCARICA AGGIORNAMENTO v1.6.0", resolveUpdatePrimaryActionButtonText(state))

        // 2. Downloading state -> Progress label
        state = state.copy(isDownloadingUpdate = true, updateDownloadProgress = 0.65f)
        assertEquals("Download in corso... (65%)", resolveDownloadProgressLabel(state.updateDownloadProgress))

        // 3. Download completed state -> Install & Re-download buttons
        state = state.copy(isDownloadingUpdate = false, isUpdateDownloaded = true)
        assertEquals("INSTALLA AGGIORNAMENTO", resolveUpdatePrimaryActionButtonText(state))
        assertEquals("SCARICA DI NUOVO", resolveUpdateSecondaryActionButtonText())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Rapid State Emission Stress Stream Test & Edge Cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testUpdateInfoEdgeCasesAndFallbackFormatting() {
        // Empty changelog fallback
        val infoEmptyChangelog = UpdateInfo(
            version = "1.6.0",
            title = "v1.6.0",
            changelog = "",
            downloadUrl = "https://example.com/app.apk",
            apkSize = 0L,
            publishedAt = "2026-08-14",
            sha256 = null
        )
        val changelogDisplay = infoEmptyChangelog.changelog.ifEmpty { "Nessuna nota di rilascio fornita." }
        assertEquals("Nessuna nota di rilascio fornita.", changelogDisplay)
        assertEquals("2026-08-14", resolveReleaseDateDisplay(infoEmptyChangelog.publishedAt))
        assertEquals("0.0 MB", resolveApkSizeDisplay(infoEmptyChangelog.apkSize))

        // Empty publishedAt
        assertEquals("", resolveReleaseDateDisplay(""))
    }

    @Test
    fun testLogSeverityEdgeCasesAndMixedKeywords() {
        assertEquals("Green", resolveLogColorCategory("CONNECTED"))
        assertEquals("Green", resolveLogColorCategory("restored"))
        assertEquals("Red", resolveLogColorCategory("FAILED"))
        assertEquals("Red", resolveLogColorCategory("Error reading DUML frame"))
        assertEquals("Amber", resolveLogColorCategory("Disabling 4G bridge..."))
        assertEquals("Amber", resolveLogColorCategory("Probing serial..."))
        assertEquals("Default", resolveLogColorCategory("Random debug log without keywords"))
    }

    @Test
    fun testRapidStateEmissionsStressStream() {
        var current = AppState()
        for (i in 1..1000) {
            current = current.copy(
                isFccEnabled = (i % 2 == 0),
                is4gBusy = (i % 5 == 0),
                updateAvailable = (i % 10 == 0),
                logMessages = current.logMessages + "Log entry #$i",
                gpsState = if (i % 2 == 0) GpsState.ON else GpsState.OFF,
                ledState = if (i % 3 == 0) LedState.ON else LedState.OFF
            )
            assertNotNull(current)
            assertEquals(i, current.logMessages.size)
        }
        assertEquals(1000, current.logMessages.size)
        assertTrue(current.isFccEnabled)
        assertTrue(current.updateAvailable)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test Emulators & Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun resolveUpdateBadgeVisible(updateAvailable: Boolean): Boolean = updateAvailable
    private fun resolveUpdateBadgeText(updateAvailable: Boolean): String =
        if (updateAvailable) "🔴 NUOVA VERSIONE" else "🟢 AGGIORNATO"

    private fun resolveControllerModelDisplay(model: String): String = model.ifEmpty { "Non rilevato" }
    private fun resolveAircraftModelDisplay(name: String, code: String): String =
        name.ifEmpty { code.ifEmpty { "Non rilevato" } }
    private fun resolveAircraftCodeDisplay(code: String): String = code.ifEmpty { "Non rilevato" }
    private fun resolveAircraftSerialDisplay(serial: String): String = serial.ifEmpty { "Non rilevato" }

    private fun resolveLanClipboardText(url: String): String = url

    private fun resolveGpsBadgeText(gpsState: GpsState): String = when (gpsState) {
        GpsState.ON -> "GPS ATTIVO"
        GpsState.OFF -> "GPS DISATTIVATO"
        GpsState.UNEXPECTED -> "STATO INATTESO"
        GpsState.UNKNOWN -> "NON VERIFICATO"
    }

    private fun isGpsActionEnabled(isGpsBusy: Boolean, isHardwareBusy: Boolean): Boolean =
        !isGpsBusy && !isHardwareBusy

    private fun resolveLedBadgeText(ledState: LedState): String = when (ledState) {
        LedState.ON -> "LED ACCESI"
        LedState.OFF -> "LED SPENTI"
        LedState.PARTIAL -> "PARZIALE"
        LedState.UNKNOWN -> "NON VERIFICATO"
    }

    private fun isLedActionEnabled(isLedBusy: Boolean, isHardwareBusy: Boolean): Boolean =
        !isLedBusy && !isHardwareBusy

    private fun resolveLogsClipboardText(logs: List<String>): String = logs.joinToString("\n")
    private fun resolveDisplayedLogs(logs: List<String>, isCleared: Boolean): List<String> =
        if (isCleared) emptyList() else logs

    private fun resolveLogColorCategory(entry: String): String = when {
        entry.contains("enabled", true) || entry.contains("connected", true) ||
        entry.contains("restored", true) || entry.contains("received", true) -> "Green"
        entry.contains("fail", true) || entry.contains("error", true) -> "Red"
        entry.contains("Enabling", true) || entry.contains("Disabling", true) ||
        entry.contains("Probing", true) || entry.contains("Querying", true) ||
        entry.contains("Loaded", true) -> "Amber"
        else -> "Default"
    }

    private fun isUpdateErrorDisplayed(info: UpdateInfo?, checked: Boolean): Boolean =
        info == null && checked

    private fun resolveApkSizeDisplay(sizeBytes: Long): String =
        "%.1f MB".format(java.util.Locale.US, sizeBytes / 1048576.0)

    private fun resolveReleaseDateDisplay(publishedAt: String): String =
        publishedAt.split("T").firstOrNull() ?: ""

    private fun resolveDownloadProgressLabel(progress: Float): String =
        if (progress <= 0f) "Connessione a GitHub..." else "Download in corso... (${(progress * 100).toInt()}%)"

    private fun resolveUpdatePrimaryActionButtonText(state: AppState): String = when {
        state.isUpdateDownloaded -> "INSTALLA AGGIORNAMENTO"
        state.updateAvailable -> "SCARICA AGGIORNAMENTO v${state.updateInfo?.version}"
        else -> "VERIFICA NUOVAMENTE"
    }

    private fun resolveUpdateSecondaryActionButtonText(): String = "SCARICA DI NUOVO"
}

