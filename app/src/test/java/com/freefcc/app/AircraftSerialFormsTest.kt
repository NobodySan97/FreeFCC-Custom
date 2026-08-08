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
        // Close behind the read, so the verify beat is not due and the Home
        // Point is the only thing that could open a window here. Holding the
        // port back while FCC owns it is not this policy's job — the service
        // gates that separately in identityReadMayRun.
        val homePointAt = readAt + 1_000L

        // The Home Point is not ripe yet.
        assertFalse(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                storedModelCode = "WA530",
                homePointAtMs = homePointAt,
                lastBusReadAtMs = readAt,
                nowMs = homePointAt + AircraftIdentityProbePolicy.HOME_POINT_GUARD_MS - 1
            )
        )
        assertTrue(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                storedModelCode = "WA530",
                homePointAtMs = homePointAt,
                lastBusReadAtMs = readAt,
                nowMs = homePointAt + AircraftIdentityProbePolicy.HOME_POINT_GUARD_MS
            )
        )
        // That Home Point is spent once the window has read the bus — checked
        // before the verify beat comes round, so this is the Home Point being
        // spent rather than a fresh verification.
        val spentAt = homePointAt + AircraftIdentityProbePolicy.HOME_POINT_GUARD_MS
        assertFalse(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                storedModelCode = "WA530",
                homePointAtMs = homePointAt,
                lastBusReadAtMs = spentAt,
                nowMs = spentAt + AircraftIdentityProbePolicy.VERIFY_INTERVAL_MS - 1
            )
        )
    }

    @Test
    fun aCompleteIdentityIsStillReAskedInCaseTheAircraftWasSwapped() {
        // Swapping to another aircraft of the same model raises no model
        // change, and a session with no fix raises no Home Point, so without
        // this the previous aircraft's serial stands in for the new one all
        // session. Asking is cheap; only a differing answer costs a listen.
        val readAt = 1_000L

        assertFalse(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                storedModelCode = "WA530",
                homePointAtMs = 0L,
                lastBusReadAtMs = readAt,
                nowMs = readAt + AircraftIdentityProbePolicy.VERIFY_INTERVAL_MS - 1
            )
        )
        assertTrue(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                storedModelCode = "WA530",
                homePointAtMs = 0L,
                lastBusReadAtMs = readAt,
                nowMs = readAt + AircraftIdentityProbePolicy.VERIFY_INTERVAL_MS
            )
        )
    }

    @Test
    fun aKnownSerialWithNoModelKeepsTryingOnTheSlowBeat() {
        // The serial can now be asked for and lands on the first window, while
        // the model still has to be overheard. Stopping at the serial left an
        // aircraft unnamed for the whole session.
        val readAt = 1_000L
        assertTrue(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                storedModelCode = "",
                homePointAtMs = 0L,
                lastBusReadAtMs = readAt,
                nowMs = readAt + AircraftIdentityProbePolicy.UNKNOWN_RETRY_INTERVAL_MS
            )
        )
        // But it stays a slow beat, not a poll of port 40007.
        assertFalse(
            AircraftIdentityProbePolicy.shouldOpenWindow(
                storedSerial = serial,
                storedModelCode = "",
                homePointAtMs = 0L,
                lastBusReadAtMs = readAt,
                nowMs = readAt + 1_000L
            )
        )
    }
}
