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
