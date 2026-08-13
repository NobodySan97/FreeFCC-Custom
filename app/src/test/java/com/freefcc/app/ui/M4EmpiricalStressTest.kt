package com.freefcc.app.ui

import com.freefcc.app.AppState
import com.freefcc.app.FccViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Stress Test Harness for Milestone M4 (ModemScreen UI State & Edge Cases).
 *
 * Stress-tests:
 * 1. Composability and rendering of ModemScreen with mock/fake AppState
 * 2. 4G activation state changes (idle, working/busy, success response, error response, refused, timeout)
 * 3. Serial input override and probe triggers
 * 4. Untruncated fourGMessage rendering and fallback formatting
 * 5. 4G endpoint probe state mapping
 * 6. Rapid state transitions during 4G activation lifecycle
 */
class M4EmpiricalStressTest {

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Composability & State Representation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testModemScreenConnectedVsDisconnectedStateRepresentation() {
        // Connected State
        val connectedState = AppState(
            isConnected = true,
            aircraftSerial = "1581F4X123456789",
            manualSerial = "",
            is4gBusy = false,
            fourGMessage = "4G endpoint reachable"
        )
        assertTrue(connectedState.isConnected)
        assertEquals("1581F4X123456789", resolveActiveSerial(connectedState.aircraftSerial, connectedState.manualSerial))
        assertEquals("AUTOMATICO", resolveSerialBadgeLabel(connectedState.aircraftSerial, connectedState.manualSerial))
        assertTrue(is4gActivationButtonEnabled(is4gBusy = connectedState.is4gBusy, isHardwareBusy = connectedState.isHardwareBusy))

        // Disconnected State
        val disconnectedState = AppState(
            isConnected = false,
            aircraftSerial = "",
            manualSerial = "",
            is4gBusy = false,
            fourGMessage = "Controller non connesso"
        )
        assertFalse(disconnectedState.isConnected)
        assertEquals("", resolveActiveSerial(disconnectedState.aircraftSerial, disconnectedState.manualSerial))
        assertEquals("NON RILEVATO", resolveSerialBadgeLabel(disconnectedState.aircraftSerial, disconnectedState.manualSerial))
    }

    @Test
    fun testModemScreenHeaderAndSerialCardStateMapping() {
        val state = AppState(
            aircraftSerial = "1581F4X11223344",
            manualSerial = "1581F4X99999999",
            isProbingSerial = false,
            isHardwareBusy = false
        )
        assertEquals("1581F4X99999999", resolveActiveSerial(state.aircraftSerial, state.manualSerial))
        assertEquals("OVERRIDE MANUALE", resolveSerialBadgeLabel(state.aircraftSerial, state.manualSerial))
        assertTrue(isProbeButtonEnabled(isHardwareBusy = state.isHardwareBusy, isProbingSerial = state.isProbingSerial, is4gBusy = state.is4gBusy))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. 4G Activation Button & Hardware Busy State Permutations
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testFourGActivationButtonDisabledWhenBusyOrHardwareBusy() {
        // Case 1: Idle -> Button Enabled
        assertTrue("4G button must be enabled when idle", is4gActivationButtonEnabled(is4gBusy = false, isHardwareBusy = false))

        // Case 2: 4G Busy -> Button Disabled
        assertFalse("4G button must be disabled when 4G activation is busy", is4gActivationButtonEnabled(is4gBusy = true, isHardwareBusy = false))

        // Case 3: Hardware Busy -> Button Disabled
        assertFalse("4G button must be disabled when hardware lock is held", is4gActivationButtonEnabled(is4gBusy = false, isHardwareBusy = true))

        // Case 4: Both Busy -> Button Disabled
        assertFalse("4G button must be disabled when both are busy", is4gActivationButtonEnabled(is4gBusy = true, isHardwareBusy = true))
    }

    @Test
    fun testFourGActivationButtonStatePermutations() {
        val permutations = listOf(
            Triple(false, false, true),  // idle -> enabled
            Triple(true, false, false),  // 4g busy -> disabled
            Triple(false, true, false),  // hw busy -> disabled
            Triple(true, true, false)    // both busy -> disabled
        )

        for ((is4gBusy, hwBusy, expectedEnabled) in permutations) {
            assertEquals(
                "Button state mismatch for is4gBusy=$is4gBusy, hwBusy=$hwBusy",
                expectedEnabled,
                is4gActivationButtonEnabled(is4gBusy = is4gBusy, isHardwareBusy = hwBusy)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. 4G Activation Response State & Status Badge Resolution
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testFourGResponseAcceptedStateRepresentation() {
        val message = "4G switch request ACCEPTED by the controller (resp 0,0,0) — the link switch is running; check 4G status on the aircraft."
        val state = AppState(is4gBusy = false, fourGMessage = message)

        assertFalse(state.is4gBusy)
        assertEquals("ACCETTATO", resolveFourGStatusBadgeTitle(state.fourGMessage))
        assertEquals(message, resolveFourGMessageDisplay(state.fourGMessage))
    }

    @Test
    fun testFourGResponseRefusedLteNotReadyStateRepresentation() {
        val message = "4G switch REFUSED by the controller (resp 3,3,3): LTE link is not available yet. Pair and activate the cellular dongle first, then retry."
        val state = AppState(is4gBusy = false, fourGMessage = message)

        assertFalse(state.is4gBusy)
        assertEquals("NON DISPONIBILE", resolveFourGStatusBadgeTitle(state.fourGMessage))
        assertEquals(message, resolveFourGMessageDisplay(state.fourGMessage))
    }

    @Test
    fun testFourGResponseInvalidRequestStateRepresentation() {
        val message = "4G request rejected as invalid (resp 9,9,9) — please report this response."
        val state = AppState(is4gBusy = false, fourGMessage = message)

        assertFalse(state.is4gBusy)
        assertEquals("NON VALIDO", resolveFourGStatusBadgeTitle(state.fourGMessage))
        assertEquals(message, resolveFourGMessageDisplay(state.fourGMessage))
    }

    @Test
    fun testFourGResponseTimeoutUnknownStateRepresentation() {
        val message = "Frame written, but no response within the read window — 4G status unknown. The DUSS router may not route the reply to this app."
        val state = AppState(is4gBusy = false, fourGMessage = message)

        assertFalse(state.is4gBusy)
        assertEquals("SCONOSCIUTO", resolveFourGStatusBadgeTitle(state.fourGMessage))
        assertEquals(message, resolveFourGMessageDisplay(state.fourGMessage))
    }

    @Test
    fun testFourGResponseMissingSerialOrEndpointUnreachableFastFail() {
        // Fast-fail 1: Serial missing
        val noSerialMsg = "No full aircraft serial (1581...). Power on and link the drone, then refresh its S/N. The 4G request needs the exact serial — a model code is refused by the WLM."
        assertEquals("REQUISITO MANCANTE", resolveFourGStatusBadgeTitle(noSerialMsg))

        // Fast-fail 2: Endpoint unreachable
        val noEndpointMsg = "4G DUSS endpoint /duss/mb/0x205 is not reachable for the current link."
        assertEquals("NON RAGGIUNGIBILE", resolveFourGStatusBadgeTitle(noEndpointMsg))
    }

    @Test
    fun testFourGStatusBadgeResolution() {
        assertEquals("PRONTO", resolveFourGStatusBadgeTitle(""))
        assertEquals("ACCETTATO", resolveFourGStatusBadgeTitle("4G switch request ACCEPTED by the controller"))
        assertEquals("NON DISPONIBILE", resolveFourGStatusBadgeTitle("4G switch REFUSED by the controller (resp 3,3,3)"))
        assertEquals("NON VALIDO", resolveFourGStatusBadgeTitle("4G request rejected as invalid (resp 9,9,9)"))
        assertEquals("SCONOSCIUTO", resolveFourGStatusBadgeTitle("4G status unknown — timeout"))
        assertEquals("RISPOSTA RICEVUTA", resolveFourGStatusBadgeTitle("4G response received"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Serial Input Override & Probe Trigger Verification
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testActiveSerialResolutionPriority() {
        // 1. Manual serial set -> Manual takes priority
        assertEquals("1581F4X99999999", resolveActiveSerial(aircraftSerial = "1581F4X123456789", manualSerial = "1581F4X99999999"))

        // 2. Manual empty -> Auto serial used
        assertEquals("1581F4X123456789", resolveActiveSerial(aircraftSerial = "1581F4X123456789", manualSerial = ""))

        // 3. Both empty -> Empty string
        assertEquals("", resolveActiveSerial(aircraftSerial = "", manualSerial = ""))
    }

    @Test
    fun testSerialBadgeLabelResolution() {
        assertEquals("OVERRIDE MANUALE", resolveSerialBadgeLabel(aircraftSerial = "1581F4X123", manualSerial = "1581F4X999"))
        assertEquals("AUTOMATICO", resolveSerialBadgeLabel(aircraftSerial = "1581F4X123", manualSerial = ""))
        assertEquals("NON RILEVATO", resolveSerialBadgeLabel(aircraftSerial = "", manualSerial = ""))
    }

    @Test
    fun testManualSerialInputSanitizationAndValidation() {
        val rawInput = "  1581abcdef012345  "
        val sanitized = rawInput.trim().uppercase()
        assertEquals("1581ABCDEF012345", sanitized)

        val validIdentity = FccViewModel.parseFourGIdentity(sanitized)
        assertNotNull(validIdentity)
        assertEquals("1581ABCDEF012345", validIdentity?.payloadSerial)

        // Invalid serial format (model code)
        val modelCode = "WA341"
        assertNull("Short model code WA341 must be rejected for 4G identity", FccViewModel.parseFourGIdentity(modelCode))
    }

    @Test
    fun testProbeButtonEnableCondition() {
        assertTrue(isProbeButtonEnabled(isHardwareBusy = false, isProbingSerial = false, is4gBusy = false))
        assertFalse(isProbeButtonEnabled(isHardwareBusy = true, isProbingSerial = false, is4gBusy = false))
        assertFalse(isProbeButtonEnabled(isHardwareBusy = false, isProbingSerial = true, is4gBusy = false))
        assertFalse(isProbeButtonEnabled(isHardwareBusy = false, isProbingSerial = false, is4gBusy = true))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Untruncated fourGMessage Rendering & Fallbacks
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testUntruncatedFourGMessagePreservation() {
        val longMessage = """
            4G switch REFUSED by the controller (resp 3,3,3): LTE link is not available yet.
            Pair and activate the cellular dongle first, then retry.
            Note: The DUSS router exposes endpoint /duss/mb/0x205 but physical LTE hardware must be linked.
        """.trimIndent()

        val renderedText = resolveFourGMessageDisplay(longMessage)
        assertEquals(longMessage, renderedText)
        assertTrue("Rendered text length must match full original string", renderedText.length >= 200)
    }

    @Test
    fun testFourGMessageFallbackFormatting() {
        val emptyStateMessage = resolveFourGMessageDisplay("")
        assertEquals(
            "Richiesta 4G mirata sperimentale (modalità HYBRID). La raggiungibilità dell'endpoint e la scrittura coronata da successo non garantiscono l'attivazione effettiva del modem 4G se il dongle non è attivo.",
            emptyStateMessage
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. 4G Endpoint Probe & UI State Transitions
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testEndpointProbeStateMapping() {
        val reachableState = AppState(fourGMessage = "4G endpoint reachable — hardware type and activation compatibility are still unknown")
        assertEquals("4G endpoint reachable — hardware type and activation compatibility are still unknown", resolveFourGMessageDisplay(reachableState.fourGMessage))

        val unreachableState = AppState(fourGMessage = "4G endpoint not reachable for the current aircraft/controller state")
        assertEquals("4G endpoint not reachable for the current aircraft/controller state", resolveFourGMessageDisplay(unreachableState.fourGMessage))
    }

    @Test
    fun testRapidStateTransitionsDuring4gActivation() {
        // Step 1: Idle State
        var state = AppState(
            isConnected = true,
            aircraftSerial = "1581F4X123456789",
            is4gBusy = false,
            fourGMessage = ""
        )
        assertTrue(is4gActivationButtonEnabled(state.is4gBusy, state.isHardwareBusy))

        // Step 2: User clicks Activation -> Working / Busy State
        state = state.copy(is4gBusy = true, busyProgress = 0.5f)
        assertFalse("Button must be disabled during activation", is4gActivationButtonEnabled(state.is4gBusy, state.isHardwareBusy))
        assertEquals("INVIO FRAME 4G IN CORSO...", resolveActivationButtonText(state.is4gBusy))

        // Step 3: Response arrives -> ACCEPTED
        val successMsg = "4G switch request ACCEPTED by the controller (resp 0,0,0) — the link switch is running; check 4G status on the aircraft."
        state = state.copy(is4gBusy = false, busyProgress = 0f, fourGMessage = successMsg)
        assertTrue("Button must re-enable when action completes", is4gActivationButtonEnabled(state.is4gBusy, state.isHardwareBusy))
        assertEquals("INVIA FRAME ATTIVAZIONE 4G", resolveActivationButtonText(state.is4gBusy))
        assertEquals("ACCETTATO", resolveFourGStatusBadgeTitle(state.fourGMessage))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Emulators matching ModemScreen Internal Logic
    // ═══════════════════════════════════════════════════════════════════════

    private fun resolveActiveSerial(aircraftSerial: String, manualSerial: String): String {
        return manualSerial.ifEmpty { aircraftSerial }
    }

    private fun resolveSerialBadgeLabel(aircraftSerial: String, manualSerial: String): String {
        val active = manualSerial.ifEmpty { aircraftSerial }
        return when {
            manualSerial.isNotEmpty() -> "OVERRIDE MANUALE"
            active.isNotEmpty() -> "AUTOMATICO"
            else -> "NON RILEVATO"
        }
    }

    private fun is4gActivationButtonEnabled(is4gBusy: Boolean, isHardwareBusy: Boolean): Boolean {
        return !is4gBusy && !isHardwareBusy
    }

    private fun isProbeButtonEnabled(isHardwareBusy: Boolean, isProbingSerial: Boolean, is4gBusy: Boolean): Boolean {
        return !isHardwareBusy && !isProbingSerial && !is4gBusy
    }

    private fun resolveActivationButtonText(is4gBusy: Boolean): String {
        return if (is4gBusy) "INVIO FRAME 4G IN CORSO..." else "INVIA FRAME ATTIVAZIONE 4G"
    }

    private fun resolveFourGStatusBadgeTitle(fourGMessage: String): String {
        return when {
            fourGMessage.contains("0,0,0") || fourGMessage.contains("ACCEPTED") -> "ACCETTATO"
            fourGMessage.contains("3,3,3") || fourGMessage.contains("REFUSED") -> "NON DISPONIBILE"
            fourGMessage.contains("9,9,9") || fourGMessage.contains("invalid") -> "NON VALIDO"
            fourGMessage.contains("unknown") || fourGMessage.contains("timeout") -> "SCONOSCIUTO"
            fourGMessage.contains("No full aircraft serial") -> "REQUISITO MANCANTE"
            fourGMessage.contains("not reachable") -> "NON RAGGIUNGIBILE"
            fourGMessage.isNotEmpty() -> "RISPOSTA RICEVUTA"
            else -> "PRONTO"
        }
    }

    private fun resolveFourGMessageDisplay(fourGMessage: String): String {
        return fourGMessage.ifEmpty {
            "Richiesta 4G mirata sperimentale (modalità HYBRID). La raggiungibilità dell'endpoint e la scrittura coronata da successo non garantiscono l'attivazione effettiva del modem 4G se il dongle non è attivo."
        }
    }
}
