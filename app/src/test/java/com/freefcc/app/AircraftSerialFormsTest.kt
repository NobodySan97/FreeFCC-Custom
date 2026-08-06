package com.freefcc.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftSerialFormsTest {
    @Test
    fun theTwoSpellingsLiveOnTheBusAreOneAircraft() {
        val full = "1581FA8JC264600B31QZ"
        val tail = "FA8JC264600B31QZ"

        assertTrue(AircraftSerialForms.sameAircraft(full, tail))
        assertTrue(AircraftSerialForms.sameAircraft(tail, full))
        assertEquals(full, AircraftSerialForms.preferred(tail, full))
        assertEquals(full, AircraftSerialForms.preferred(full, tail))
    }

    @Test
    fun differentAircraftAreNotMergedByAShortTail() {
        assertFalse(
            AircraftSerialForms.sameAircraft(
                "1581FA8JC264600B31QZ",
                "1581F9DEC25AQ02998T5"
            )
        )
        // A coincidental short ending is not a serial tail.
        assertFalse(AircraftSerialForms.sameAircraft("1581FA8JC264600B31QZ", "31QZ"))
        assertFalse(AircraftSerialForms.sameAircraft("", "FA8JC264600B31QZ"))
    }
}

class AircraftIdentityProbePolicyTest {
    private val serial = "1581FA8JC264600B31QZ"

    @Test
    fun anUnknownSerialOpensTheWindowAtOnce() {
        assertTrue(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = "",
                homePointAtMs = 0L,
                lastBusReadAtMs = 0L,
                nowMs = 1_100L
            )
        )
    }

    @Test
    fun anUnknownSerialBacksOffAfterASpentWindow() {
        val spentAt = 1_000_000L

        // Reopening on the next screen scan would poll port 40007 for the whole
        // session, and live evidence says that costs the aircraft link.
        assertFalse(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = "",
                homePointAtMs = 0L,
                lastBusReadAtMs = spentAt,
                nowMs = spentAt + 1_000L
            )
        )
        assertTrue(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = "",
                homePointAtMs = 0L,
                lastBusReadAtMs = spentAt,
                nowMs = spentAt + AircraftIdentityProbePolicy.UNKNOWN_RETRY_INTERVAL_MS
            )
        )
    }

    @Test
    fun aKnownSerialIsRereadOncePerHomePointAndOnlyAfterTheFccGuard() {
        val readAt = 1_000_000L
        val homePointAt = readAt + 120_000L

        // While the FCC write owns the port, identity waits.
        assertFalse(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                homePointAtMs = homePointAt,
                lastBusReadAtMs = readAt,
                nowMs = homePointAt + AircraftIdentityProbePolicy.HOME_POINT_GUARD_MS - 1
            )
        )
        assertTrue(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                homePointAtMs = homePointAt,
                lastBusReadAtMs = readAt,
                nowMs = homePointAt + AircraftIdentityProbePolicy.HOME_POINT_GUARD_MS
            )
        )
        // That Home Point is spent once the window has read the bus.
        assertFalse(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                homePointAtMs = homePointAt,
                lastBusReadAtMs = homePointAt + AircraftIdentityProbePolicy.HOME_POINT_GUARD_MS,
                nowMs = homePointAt + 10 * 60_000L
            )
        )
    }

    @Test
    fun aKnownSerialWithoutAHomePointNeverOpensAWindow() {
        assertFalse(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                homePointAtMs = 0L,
                lastBusReadAtMs = 1_000L,
                nowMs = 1_000L + 60 * 60_000L
            )
        )
    }
}
