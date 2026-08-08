package com.freefcc.app

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The case that matters on Lito X1: a firmware that answers only to the short
 * name. Reading has to find that name, and the write that follows has to use
 * it — otherwise the switch is a no-op no matter how many times it is pressed.
 */
class ParameterAddressResolutionTest {

    private val canonicalGps = ParameterHash.of("g_config.gps_cfg.gps_enable")
    private val shortGps = ParameterHash.of("gps_enable")

    @After
    fun forgetWhatTheAircraftProved() {
        ParameterAddress.GPS_ENABLE.forgetConfirmed()
        ParameterAddress.FOREARM_LED.forgetConfirmed()
    }

    /** Answers a bare status byte for every hash except [known], as the aircraft does. */
    private fun aircraftKnowing(known: ByteArray, value: Int): (ByteArray) -> ByteArray? =
        { requestedHash ->
            if (requestedHash.contentEquals(known)) {
                byteArrayOf(0) + known + value.toByte()
            } else {
                byteArrayOf(0)
            }
        }

    private fun readThrough(
        address: ParameterAddress,
        aircraft: (ByteArray) -> ByteArray?
    ): GpsReadback? {
        for (hash in address.candidates) {
            GpsControlProtocol.parse(aircraft(hash))?.let { return it }
        }
        return null
    }

    @Test
    fun aFreshProcessWritesWithTheCanonicalName() {
        val write = GpsControlProtocol.buildWriteRequest(enabled = true)

        assertArrayEquals(canonicalGps, write.copyOfRange(11, 15))
    }

    @Test
    fun readingAnAircraftThatOnlyKnowsTheShortNameSwitchesTheWriteToIt() {
        val readback = readThrough(
            ParameterAddress.GPS_ENABLE,
            aircraftKnowing(shortGps, value = 1)
        )

        assertNotNull(readback)
        assertEquals(GpsState.ON, readback?.state)

        // The write built after the read must carry the name that answered.
        val write = GpsControlProtocol.buildWriteRequest(enabled = false)
        assertArrayEquals(shortGps, write.copyOfRange(11, 15))
        assertEquals(0x00.toByte(), write[15])
        assertEquals(0xF9.toByte(), write[10])
    }

    @Test
    fun anAircraftThatKnowsTheCanonicalNameKeepsUsingIt() {
        val readback = readThrough(
            ParameterAddress.GPS_ENABLE,
            aircraftKnowing(canonicalGps, value = 0)
        )

        assertEquals(GpsState.OFF, readback?.state)
        assertArrayEquals(
            canonicalGps,
            GpsControlProtocol.buildWriteRequest(enabled = true).copyOfRange(11, 15)
        )
    }

    @Test
    fun anAircraftThatAnswersToNeitherNameLeavesTheWriteCanonical() {
        val unknown = ParameterHash.of("some_parameter_no_firmware_has")

        val readback = readThrough(ParameterAddress.GPS_ENABLE, aircraftKnowing(unknown, value = 1))

        assertNull(readback)
        assertArrayEquals(
            canonicalGps,
            GpsControlProtocol.buildWriteRequest(enabled = true).copyOfRange(11, 15)
        )
    }

    @Test
    fun ledAndGpsResolveIndependently() {
        val shortLed = ParameterHash.of("forearm_led_ctrl")

        LedReadbackProtocol.parse(byteArrayOf(0) + shortLed + 0xEF.toByte())

        assertArrayEquals(shortLed, ParameterAddress.FOREARM_LED.preferred())
        // A resolved LED name must not drag GPS along with it.
        assertArrayEquals(canonicalGps, ParameterAddress.GPS_ENABLE.preferred())
    }
}
