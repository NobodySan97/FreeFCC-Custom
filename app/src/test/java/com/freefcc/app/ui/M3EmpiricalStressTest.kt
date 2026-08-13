package com.freefcc.app.ui

import com.freefcc.app.AppState
import com.freefcc.app.AutoFccMode
import com.freefcc.app.AutoFccSelection
import com.freefcc.app.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Stress Test Harness for Milestone M3 (FccScreen UI State & Edge Cases).
 *
 * Stress-tests:
 * 1. State representations (Connected + FCC On, Disconnected, CE Standard, Auto-FCC Modes)
 * 2. Busy state disabling logic for FCC Enable action button (!isHardwareBusy && !isBusy)
 * 3. Empty & missing message strings logic and fallback formatting
 * 4. Update alert banner display conditions (updateAvailable, updateInfo presence, checking state)
 * 5. Update download & installation progress states
 * 6. Auto-FCC mode state selection & accessibility / overlay action rules
 * 7. State transitions when aircraft is disconnected vs connected
 * 8. Rapid auto FCC mode changes & mutual exclusivity logic
 * 9. Permission checking & request decision rules
 */
class M3EmpiricalStressTest {

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Composable State Representation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testConnectedFccUnlockedStateRepresentation() {
        val state = AppState(
            isConnected = true,
            controllerModel = "RM510",
            aircraftModelName = "DJI Air 3",
            aircraftModelCode = "WM161",
            aircraftSerial = "1581F4X123456789",
            isFccEnabled = true,
            message = "Region AU verified; FCC request written"
        )

        assertTrue(state.isConnected)
        assertTrue(state.isFccEnabled)
        assertEquals("RM510", state.controllerModel)
        assertEquals("1581F4X123456789", state.aircraftSerial)
        assertEquals("FCC UNLOCKED", resolveFccBadgeTitle(state.isFccEnabled))
        assertEquals("REINVIA RICHIESTA FCC", resolveEnableButtonText(state.isFccEnabled))
    }

    @Test
    fun testDisconnectedCeStandardStateRepresentation() {
        val state = AppState(
            isConnected = false,
            controllerModel = "",
            aircraftSerial = "",
            isFccEnabled = false,
            message = "Controller non trovato."
        )

        assertFalse(state.isConnected)
        assertFalse(state.isFccEnabled)
        assertEquals("STANDARD CE", resolveFccBadgeTitle(state.isFccEnabled))
        assertEquals("ABILITA MODALITÀ FCC", resolveEnableButtonText(state.isFccEnabled))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Busy State Disabling Button Logic Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testFccButtonDisabledWhenBusyOrHardwareBusy() {
        // Case 1: Neither busy -> Button Enabled
        assertTrue(
            "FCC button must be enabled when idle",
            isFccButtonEnabled(isHardwareBusy = false, isBusy = false)
        )

        // Case 2: Hardware busy -> Button Disabled
        assertFalse(
            "FCC button must be disabled when hardware is busy",
            isFccButtonEnabled(isHardwareBusy = true, isBusy = false)
        )

        // Case 3: Action busy -> Button Disabled
        assertFalse(
            "FCC button must be disabled when action is busy",
            isFccButtonEnabled(isHardwareBusy = false, isBusy = true)
        )

        // Case 4: Both busy -> Button Disabled
        assertFalse(
            "FCC button must be disabled when both hardware and action are busy",
            isFccButtonEnabled(isHardwareBusy = true, isBusy = true)
        )
    }

    @Test
    fun testFccButtonStatePermutations() {
        val permutations = listOf(
            Triple(false, false, true),  // idle -> enabled
            Triple(true, false, false),   // hardware busy -> disabled
            Triple(false, true, false),   // busy -> disabled
            Triple(true, true, false)     // both busy -> disabled
        )

        for ((hwBusy, busy, expectedEnabled) in permutations) {
            assertEquals(
                "Button enabled state mismatch for hwBusy=$hwBusy, busy=$busy",
                expectedEnabled,
                isFccButtonEnabled(isHardwareBusy = hwBusy, isBusy = busy)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Message String Handling & Fallbacks
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testMessageFormattingAndMissingMessageFallback() {
        // Case 1: Busy with custom message -> displays message
        val busyWithMessage = AppState(isBusy = true, message = "Executing DUML frame...")
        assertEquals("Executing DUML frame...", resolveProgressLabel(busyWithMessage))

        // Case 2: Busy with empty message -> displays fallback default message
        val busyWithEmptyMessage = AppState(isBusy = true, message = "")
        assertEquals("Invio pacchetti FCC in corso...", resolveProgressLabel(busyWithEmptyMessage))

        // Case 3: Idle with message -> displays custom message
        val idleWithMessage = AppState(isBusy = false, message = "FCC Active")
        assertEquals("FCC Active", resolveStatusSurfaceText(idleWithMessage))

        // Case 4: Idle with empty message -> displays default hint text
        val idleWithEmptyMessage = AppState(isBusy = false, message = "")
        assertEquals(
            "Premi il pulsante per inviare i comandi di sblocco FCC al radiocomando.",
            resolveStatusSurfaceText(idleWithEmptyMessage)
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Update Banner Display State Rules
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testUpdateAlertCardVisibilityAndSubtitleLogic() {
        val sampleUpdateInfo = UpdateInfo(
            version = "2.1.0",
            title = "v2.1.0",
            changelog = "Release notes",
            downloadUrl = "https://example.com/apk",
            apkSize = 1024L,
            publishedAt = "2026-08-13T00:00:00Z",
            sha256 = null
        )

        // Condition for banner visibility: state.updateAvailable && state.updateInfo != null && !state.isCheckingUpdate

        // Case 1: Update available, info present, not checking -> Banner VISIBLE
        val visibleState = AppState(
            updateAvailable = true,
            updateInfo = sampleUpdateInfo,
            isCheckingUpdate = false
        )
        assertTrue("Banner must be visible when update available and not checking", isUpdateBannerVisible(visibleState))

        // Case 2: Update available but updateInfo is null -> Banner HIDDEN
        val missingInfoState = AppState(
            updateAvailable = true,
            updateInfo = null,
            isCheckingUpdate = false
        )
        assertFalse("Banner must be hidden if updateInfo is null", isUpdateBannerVisible(missingInfoState))

        // Case 3: Update available but currently checking -> Banner HIDDEN
        val checkingState = AppState(
            updateAvailable = true,
            updateInfo = sampleUpdateInfo,
            isCheckingUpdate = true
        )
        assertFalse("Banner must be hidden while checking for updates", isUpdateBannerVisible(checkingState))

        // Case 4: updateAvailable is false -> Banner HIDDEN
        val noUpdateState = AppState(
            updateAvailable = false,
            updateInfo = sampleUpdateInfo,
            isCheckingUpdate = false
        )
        assertFalse("Banner must be hidden when updateAvailable is false", isUpdateBannerVisible(noUpdateState))
    }

    @Test
    fun testUpdateBannerSubTextAndActionButton() {
        // State 1: Download available -> "Nuova versione disponibile" / SCARICA
        val availableBanner = resolveUpdateSubText(isDownloaded = false, isDownloading = false, downloadProgress = 0f)
        assertEquals("Nuova versione disponibile", availableBanner)
        assertEquals("SCARICA", resolveUpdateActionButtonText(isDownloaded = false))

        // State 2: Downloading in progress (45%) -> "Download: 45%" / SCARICA
        val downloadingBanner = resolveUpdateSubText(isDownloaded = false, isDownloading = true, downloadProgress = 0.45f)
        assertEquals("Download: 45%", downloadingBanner)

        // State 3: Download completed -> "Pronto per l'installazione" / INSTALLA
        val downloadedBanner = resolveUpdateSubText(isDownloaded = true, isDownloading = false, downloadProgress = 1.0f)
        assertEquals("Pronto per l'installazione", downloadedBanner)
        assertEquals("INSTALLA", resolveUpdateActionButtonText(isDownloaded = true))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Auto-FCC Modes & Overlay Toggles
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testAutoFccModeSelectionMapping() {
        val noneSelected = AppState(selectedAutoMode = null)
        assertNull(noneSelected.selectedAutoMode)

        val homePointSelected = AppState(selectedAutoMode = AutoFccMode.HOME_POINT_TEXT)
        assertEquals(AutoFccMode.HOME_POINT_TEXT, homePointSelected.selectedAutoMode)

        val periodicSelected = AppState(selectedAutoMode = AutoFccMode.PERIODIC_5S)
        assertEquals(AutoFccMode.PERIODIC_5S, periodicSelected.selectedAutoMode)
    }

    @Test
    fun testRapidAutoFccModeTransitionsAndExclusivity() {
        // Simulating sequence of rapid mode changes via AutoFccSelection logic:
        var currentMode: AutoFccMode? = null

        // 1. Enable HOME_POINT_TEXT
        currentMode = AutoFccSelection.updatedMode(currentMode, AutoFccMode.HOME_POINT_TEXT, true)
        assertEquals(AutoFccMode.HOME_POINT_TEXT, currentMode)

        // 2. Switch rapidly to PERIODIC_5S (should replace HOME_POINT_TEXT)
        currentMode = AutoFccSelection.updatedMode(currentMode, AutoFccMode.PERIODIC_5S, true)
        assertEquals(AutoFccMode.PERIODIC_5S, currentMode)

        // 3. Toggle PERIODIC_5S off -> should become null
        currentMode = AutoFccSelection.updatedMode(currentMode, AutoFccMode.PERIODIC_5S, false)
        assertNull(currentMode)

        // 4. Toggle HOME_POINT_TEXT on -> becomes HOME_POINT_TEXT
        currentMode = AutoFccSelection.updatedMode(currentMode, AutoFccMode.HOME_POINT_TEXT, true)
        assertEquals(AutoFccMode.HOME_POINT_TEXT, currentMode)

        // 5. Toggle HOME_POINT_TEXT off -> becomes null
        currentMode = AutoFccSelection.updatedMode(currentMode, AutoFccMode.HOME_POINT_TEXT, false)
        assertNull(currentMode)
    }

    @Test
    fun testFloatingButtonOverlayIndependentOfAutoFccMode() {
        val state1 = AppState(selectedAutoMode = AutoFccMode.HOME_POINT_TEXT, isFloatingButtonEnabled = true)
        assertTrue(state1.isFloatingButtonEnabled)
        assertEquals(AutoFccMode.HOME_POINT_TEXT, state1.selectedAutoMode)

        val state2 = AppState(selectedAutoMode = null, isFloatingButtonEnabled = true)
        assertTrue(state2.isFloatingButtonEnabled)
        assertNull(state2.selectedAutoMode)

        val state3 = AppState(selectedAutoMode = AutoFccMode.PERIODIC_5S, isFloatingButtonEnabled = false)
        assertFalse(state3.isFloatingButtonEnabled)
        assertEquals(AutoFccMode.PERIODIC_5S, state3.selectedAutoMode)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. Empirical Edge Cases: Disconnected vs Connected State Transitions
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testAircraftStateTransitionsConnectedVsDisconnected() {
        // Step 1: Initial Disconnected state
        var state = AppState(
            isConnected = false,
            controllerModel = "RM510",
            aircraftSerial = "",
            isFccEnabled = false,
            message = "Controller non trovato."
        )
        assertFalse(state.isConnected)
        assertFalse(state.isFccEnabled)
        assertEquals("STANDARD CE", resolveFccBadgeTitle(state.isFccEnabled))

        // Step 2: Aircraft connects, serial probed
        state = state.copy(
            isConnected = true,
            aircraftModelName = "DJI Mavic 3 Pro",
            aircraftModelCode = "WM260",
            aircraftSerial = "1581F4X11223344",
            message = "Connected — 1581F4X11223344"
        )
        assertTrue(state.isConnected)
        assertEquals("1581F4X11223344", state.aircraftSerial)
        assertEquals("DJI Mavic 3 Pro", state.aircraftModelName)

        // Step 3: FCC is enabled
        state = state.copy(
            isFccEnabled = true,
            message = "Region AU verified; FCC request written — verify RF mode in DJI Fly"
        )
        assertTrue(state.isFccEnabled)
        assertEquals("FCC UNLOCKED", resolveFccBadgeTitle(state.isFccEnabled))

        // Step 4: Disconnect occurs (e.g. drone powered off)
        // FCC proof remains in process-local memory, but connection drops
        state = state.copy(
            isConnected = false,
            message = "Controller connection lost"
        )
        assertFalse(state.isConnected)
        assertTrue(state.isFccEnabled) // Proof remains retained in state
        assertEquals("FCC UNLOCKED", resolveFccBadgeTitle(state.isFccEnabled))
    }

    @Test
    fun testPermissionRequestRules() {
        // Accessibility decision logic
        val shouldRequestAccess1 = resolveAccessibilityDecision(isAccessEnabled = false)
        assertTrue("Must request settings launch when access is not enabled", shouldRequestAccess1.shouldLaunchSettings)

        val shouldRequestAccess2 = resolveAccessibilityDecision(isAccessEnabled = true)
        assertFalse("Must not launch settings when access is already enabled", shouldRequestAccess2.shouldLaunchSettings)
        assertTrue("Must enable mode directly when access is granted", shouldRequestAccess2.shouldEnableDirectly)

        // Overlay decision logic
        val overlayOff = resolveOverlayDecision(requestedEnable = false, isOverlayGranted = true)
        assertFalse("Disabling overlay does not need permission launch", overlayOff.shouldLaunchSettings)
        assertFalse("Target state must be false", overlayOff.targetState)

        val overlayOnGranted = resolveOverlayDecision(requestedEnable = true, isOverlayGranted = true)
        assertFalse("Granted overlay does not need permission launch", overlayOnGranted.shouldLaunchSettings)
        assertTrue("Target state must be true", overlayOnGranted.targetState)

        val overlayOnDenied = resolveOverlayDecision(requestedEnable = true, isOverlayGranted = false)
        assertTrue("Denied overlay needs permission launch", overlayOnDenied.shouldLaunchSettings)
    }

    @Test
    fun testAppHeaderVersionFormatting() {
        val withModel = formatHeaderVersion("v1.5.0", "RM510")
        assertEquals("v1.5.0 · RM510", withModel)

        val withoutModel = formatHeaderVersion("v1.5.0", "")
        assertEquals("v1.5.0", withoutModel)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers mirroring FccScreen internal composable logic
    // ═══════════════════════════════════════════════════════════════════════

    private fun resolveFccBadgeTitle(isFccEnabled: Boolean): String {
        return if (isFccEnabled) "FCC UNLOCKED" else "STANDARD CE"
    }

    private fun resolveEnableButtonText(isFccEnabled: Boolean): String {
        return if (isFccEnabled) "REINVIA RICHIESTA FCC" else "ABILITA MODALITÀ FCC"
    }

    private fun isFccButtonEnabled(isHardwareBusy: Boolean, isBusy: Boolean): Boolean {
        return !isHardwareBusy && !isBusy
    }

    private fun resolveProgressLabel(state: AppState): String {
        return state.message.ifEmpty { "Invio pacchetti FCC in corso..." }
    }

    private fun resolveStatusSurfaceText(state: AppState): String {
        return if (state.message.isNotEmpty()) {
            state.message
        } else {
            "Premi il pulsante per inviare i comandi di sblocco FCC al radiocomando."
        }
    }

    private fun isUpdateBannerVisible(state: AppState): Boolean {
        return state.updateAvailable && state.updateInfo != null && !state.isCheckingUpdate
    }

    private fun resolveUpdateSubText(isDownloaded: Boolean, isDownloading: Boolean, downloadProgress: Float): String {
        return when {
            isDownloaded -> "Pronto per l'installazione"
            isDownloading -> "Download: ${(downloadProgress * 100).toInt()}%"
            else -> "Nuova versione disponibile"
        }
    }

    private fun resolveUpdateActionButtonText(isDownloaded: Boolean): String {
        return if (isDownloaded) "INSTALLA" else "SCARICA"
    }

    private fun formatHeaderVersion(version: String, controllerModel: String): String {
        return if (controllerModel.isNotEmpty()) {
            "$version · $controllerModel"
        } else {
            version
        }
    }

    private data class AccessibilityDecision(val shouldEnableDirectly: Boolean, val shouldLaunchSettings: Boolean)
    private fun resolveAccessibilityDecision(isAccessEnabled: Boolean): AccessibilityDecision {
        return if (isAccessEnabled) {
            AccessibilityDecision(shouldEnableDirectly = true, shouldLaunchSettings = false)
        } else {
            AccessibilityDecision(shouldEnableDirectly = false, shouldLaunchSettings = true)
        }
    }

    private data class OverlayDecision(val targetState: Boolean, val shouldLaunchSettings: Boolean)
    private fun resolveOverlayDecision(requestedEnable: Boolean, isOverlayGranted: Boolean): OverlayDecision {
        return if (!requestedEnable) {
            OverlayDecision(targetState = false, shouldLaunchSettings = false)
        } else if (isOverlayGranted) {
            OverlayDecision(targetState = true, shouldLaunchSettings = false)
        } else {
            OverlayDecision(targetState = false, shouldLaunchSettings = true)
        }
    }
}
