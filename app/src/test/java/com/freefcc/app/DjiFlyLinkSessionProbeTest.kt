package com.freefcc.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjiFlyLinkUiClassifierTest {
    @Test
    fun russianFlightModeMeansConnected() {
        assertEquals(
            DjiFlyLinkUiState.CONNECTED,
            DjiFlyLinkUiClassifier.classify(listOf("FPV Back", "Режим N", "Top Bar Rc Signal"))
        )
    }

    @Test
    fun explicitDisconnectOutranksAStaleFlightMode() {
        assertEquals(
            DjiFlyLinkUiState.DISCONNECTED,
            DjiFlyLinkUiClassifier.classify(
                listOf("Режим N", "Пульт не подключен к мобильному устройству")
            )
        )
    }

    @Test
    fun cameraNaDoesNotOverrideAConnectedFlightMode() {
        assertEquals(
            DjiFlyLinkUiState.CONNECTED,
            DjiFlyLinkUiClassifier.classify(listOf("Режим N", "Camera Mode Switch", "N/A"))
        )
    }

    @Test
    fun naMeansTheDjiFlyApplicationLinkIsDisconnected() {
        assertEquals(
            DjiFlyLinkUiState.DISCONNECTED,
            DjiFlyLinkUiClassifier.classify(listOf("N/A"))
        )
    }
}

class DjiFlyLinkSessionProbeGateTest {
    @Test
    fun probesOnlyOnceWhileTheAircraftStaysConnected() {
        val gate = DjiFlyLinkSessionProbeGate()

        assertTrue(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 1_000L))
        assertFalse(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 121_000L))
        assertFalse(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 601_000L))
    }

    @Test
    fun aBriefProbeInducedDropDoesNotRearm() {
        val gate = DjiFlyLinkSessionProbeGate(stableDisconnectMs = 10_000L)

        assertTrue(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 1_000L))
        assertFalse(gate.onUiState(DjiFlyLinkUiState.DISCONNECTED, 2_000L))
        assertFalse(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 3_000L))
    }

    @Test
    fun naMustLastTenSecondsBeforeItRearmsTheProbe() {
        val gate = DjiFlyLinkSessionProbeGate(stableDisconnectMs = 10_000L)

        assertTrue(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 1_000L))
        assertFalse(gate.onUiState(DjiFlyLinkUiState.DISCONNECTED, 2_000L))
        assertFalse(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 11_999L))

        assertFalse(gate.onUiState(DjiFlyLinkUiState.DISCONNECTED, 20_000L))
        assertTrue(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 30_000L))
    }

    @Test
    fun aRealDisconnectAllowsOneProbeOnTheNextConnection() {
        val gate = DjiFlyLinkSessionProbeGate(stableDisconnectMs = 10_000L)

        assertTrue(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 1_000L))
        assertFalse(gate.onUiState(DjiFlyLinkUiState.DISCONNECTED, 5_000L))
        assertTrue(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 20_000L))
        assertFalse(gate.onUiState(DjiFlyLinkUiState.CONNECTED, 200_000L))
    }
}
