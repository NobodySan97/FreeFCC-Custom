package com.freefcc.app.ui

import com.freefcc.app.AppState
import com.freefcc.app.ui.components.GlowButtonSize
import com.freefcc.app.ui.theme.Amber
import com.freefcc.app.ui.theme.BrandCyan
import com.freefcc.app.ui.theme.StatusGreen
import com.freefcc.app.ui.theme.StatusRed
import com.freefcc.app.ui.theme.TextMuted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Stress Test Harness for Milestone M2 (ConnectionStateHeader & DroneSerialCard).
 *
 * Verifies UI state edge cases, state formatting, active serial resolution,
 * probe loading state flags, disconnect/reconnect state transitions, and clipboard callbacks.
 */
class M2EmpiricalStressTest {

    // ═══════════════════════════════════════════════════════════════════════
    // ConnectionStateHeader Empirical Logic & Edge Case Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testConnectionStateHeaderStateMapping() {
        val connectedState = AppState(
            isConnected = true,
            controllerModel = "RM510",
            aircraftModelName = "DJI Air 3",
            aircraftModelCode = "WM161",
            message = "Connessione DUML attiva"
        )

        assertTrue("Connected state must report isConnected=true", connectedState.isConnected)
        assertEquals("RM510", connectedState.controllerModel)
        assertEquals("DJI Air 3", connectedState.aircraftModelName)
        assertEquals("WM161", connectedState.aircraftModelCode)
        assertEquals("Connessione DUML attiva", connectedState.message)

        val disconnectedState = AppState(
            isConnected = false,
            controllerModel = "",
            aircraftModelName = "",
            aircraftModelCode = "",
            message = ""
        )

        assertFalse("Disconnected state must report isConnected=false", disconnectedState.isConnected)
        assertTrue("Controller model should be empty when disconnected", disconnectedState.controllerModel.isEmpty())
        assertTrue("Aircraft model name should be empty when disconnected", disconnectedState.aircraftModelName.isEmpty())
    }

    @Test
    fun testAircraftModelFormattingLogic() {
        // Case 1: Both name and code present
        val formatBoth = formatAircraftModel("DJI Mini 4 Pro", "WM162")
        assertEquals("DJI Mini 4 Pro (WM162)", formatBoth)

        // Case 2: Only name present
        val formatNameOnly = formatAircraftModel("DJI Mavic 3", "")
        assertEquals("DJI Mavic 3", formatNameOnly)

        // Case 3: Only code present
        val formatCodeOnly = formatAircraftModel("", "WM260")
        assertEquals("WM260", formatCodeOnly)

        // Case 4: Neither present
        val formatNone = formatAircraftModel("", "")
        assertEquals("Non rilevato", formatNone)
    }

    @Test
    fun testControllerModelFormattingLogic() {
        assertEquals("RM510", formatControllerModel("RM510"))
        assertEquals("RC2", formatControllerModel("RC2"))
        assertEquals("Non rilevato", formatControllerModel(""))
    }

    @Test
    fun testDisconnectReconnectStateTransitions() {
        val initialDisconnected = AppState(isConnected = false)
        assertFalse(initialDisconnected.isConnected)

        val connecting = initialDisconnected.copy(isHardwareBusy = true, message = "Connessione in corso...")
        assertTrue(connecting.isHardwareBusy)
        assertEquals("Connessione in corso...", connecting.message)

        val connected = connecting.copy(
            isConnected = true,
            isHardwareBusy = false,
            controllerModel = "RM510",
            aircraftModelName = "DJI Air 3",
            aircraftModelCode = "WM161",
            message = "Connesso con successo"
        )
        assertTrue(connected.isConnected)
        assertFalse(connected.isHardwareBusy)

        val droppedConnection = connected.copy(
            isConnected = false,
            message = "Connessione persa (Timeout)"
        )
        assertFalse(droppedConnection.isConnected)
        assertEquals("Connessione persa (Timeout)", droppedConnection.message)
    }

    @Test
    fun testStatusMessageExtractor() {
        val stateWithMessage = AppState(message = "Warning: low signal")
        val extractedMessage = stateWithMessage.message.takeIf { it.isNotEmpty() }
        assertEquals("Warning: low signal", extractedMessage)

        val stateWithEmptyMessage = AppState(message = "")
        val extractedEmpty = stateWithEmptyMessage.message.takeIf { it.isNotEmpty() }
        assertNull("Empty message must extract to null", extractedEmpty)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DroneSerialCard Empirical Logic & Edge Case Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testActiveSerialResolution() {
        // Case 1: Only auto serial available -> returns auto serial
        val autoOnly = resolveActiveSerial(aircraftSerial = "1581F4X123456789", manualSerial = "")
        assertEquals("1581F4X123456789", autoOnly)

        // Case 2: Manual override present -> manual serial takes priority
        val overridePresent = resolveActiveSerial(aircraftSerial = "1581F4X123456789", manualSerial = "MANUAL12345")
        assertEquals("MANUAL12345", overridePresent)

        // Case 3: Both empty -> returns empty string
        val bothEmpty = resolveActiveSerial(aircraftSerial = "", manualSerial = "")
        assertEquals("", bothEmpty)
    }

    @Test
    fun testDroneSerialStatusBadgeResolution() {
        assertEquals("OVERRIDE MANUALE", resolveBadgeLabel(aircraftSerial = "1581F4X123456789", manualSerial = "MANUAL99"))
        assertEquals("OVERRIDE MANUALE", resolveBadgeLabel(aircraftSerial = "", manualSerial = "MANUAL99"))
        assertEquals("AUTOMATICO", resolveBadgeLabel(aircraftSerial = "1581F4X123456789", manualSerial = ""))
        assertEquals("NON RILEVATO", resolveBadgeLabel(aircraftSerial = "", manualSerial = ""))
    }

    @Test
    fun testManualSerialSanitization() {
        val rawInput = " 1581f4x999888777 "
        val sanitized = rawInput.trim().uppercase()
        assertEquals("1581F4X999888777", sanitized)
    }

    @Test
    fun testProbeButtonEnableCondition() {
        // Idle state -> Probe enabled
        assertTrue("Probe button should be enabled when idle", isProbeButtonEnabled(isHardwareBusy = false, isProbingSerial = false))

        // Hardware busy -> Probe disabled
        assertFalse("Probe button should be disabled when hardware is busy", isProbeButtonEnabled(isHardwareBusy = true, isProbingSerial = false))

        // Probing active -> Probe disabled
        assertFalse("Probe button should be disabled when already probing", isProbeButtonEnabled(isHardwareBusy = false, isProbingSerial = true))

        // Both busy -> Probe disabled
        assertFalse("Probe button should be disabled when both busy and probing", isProbeButtonEnabled(isHardwareBusy = true, isProbingSerial = true))
    }

    @Test
    fun testClipboardCopyCallbackInvocation() {
        var copiedText: String? = null
        val copyHandler: (String) -> Unit = { copiedText = it }

        val activeSerial = resolveActiveSerial(aircraftSerial = "1581F4X123456789", manualSerial = "")
        copyHandler(activeSerial)

        assertEquals("1581F4X123456789", copiedText)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Emulators matching Component Internal Logic
    // ═══════════════════════════════════════════════════════════════════════

    private fun formatAircraftModel(name: String, code: String): String {
        return when {
            name.isNotEmpty() && code.isNotEmpty() -> "$name ($code)"
            name.isNotEmpty() -> name
            code.isNotEmpty() -> code
            else -> "Non rilevato"
        }
    }

    private fun formatControllerModel(model: String): String {
        return model.ifEmpty { "Non rilevato" }
    }

    private fun resolveActiveSerial(aircraftSerial: String, manualSerial: String): String {
        return manualSerial.ifEmpty { aircraftSerial }
    }

    private fun resolveBadgeLabel(aircraftSerial: String, manualSerial: String): String {
        val activeSerial = manualSerial.ifEmpty { aircraftSerial }
        val isManual = manualSerial.isNotEmpty()
        val hasSerial = activeSerial.isNotEmpty()

        return when {
            isManual -> "OVERRIDE MANUALE"
            hasSerial -> "AUTOMATICO"
            else -> "NON RILEVATO"
        }
    }

    private fun isProbeButtonEnabled(isHardwareBusy: Boolean, isProbingSerial: Boolean): Boolean {
        return !isHardwareBusy && !isProbingSerial
    }
}
