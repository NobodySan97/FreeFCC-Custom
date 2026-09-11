package com.freefcc.app

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedProfileHashTest {

    private val canonical = ParameterHash.of("g_config.misc_cfg.forearm_lamp_ctrl")
    private val short = ParameterHash.of("forearm_led_ctrl")

    private fun ledOnFrame(hash: ByteArray = canonical): ByteArray =
        DumlBuilder().buildFrame(
            DumlFrame(
                sender = 0x02,
                cmdType = 0x40,
                cmdSet = 0x03,
                cmdId = 0xF9,
                dst = 0x03,
                payload = hash + 0xEF.toByte()
            )
        )

    private fun profileOf(vararg frames: ByteArray) = Profiles.Profile(
        sender = 0x02,
        cmdType = 0x40,
        rounds = 1,
        interFrameDelay = 0,
        interRoundDelay = 0,
        readWindowMs = 100,
        needsResponse = false,
        port = DumlTransport.PORT_LED,
        frames = frames.toList()
    )

    private fun crcIsValid(frame: ByteArray, innerStart: Int = 0): Boolean {
        val expected = DumlBuilder.crc16(frame, innerStart, frame.size - 2 - innerStart)
        val actual = (frame[frame.size - 2].toInt() and 0xFF) or
            ((frame[frame.size - 1].toInt() and 0xFF) shl 8)
        return expected == actual
    }

    @After
    fun forgetWhatTheAircraftProved() {
        ParameterAddress.FOREARM_LED.forgetConfirmed()
    }

    @Test
    fun leavesTheProfileAloneUntilAnAircraftProvesTheOtherName() {
        val profile = profileOf(ledOnFrame())

        val result = LedProfileHash.retargeted(profile, ParameterAddress.FOREARM_LED)

        assertArrayEquals(profile.frames.first(), result.frames.first())
    }

    @Test
    fun writesToTheNameTheAircraftAnsweredTo() {
        ParameterAddress.FOREARM_LED.confirm(short)
        val original = ledOnFrame()

        val rewritten = LedProfileHash
            .retargeted(profileOf(original), ParameterAddress.FOREARM_LED)
            .frames
            .first()

        assertArrayEquals(short, rewritten.copyOfRange(11, 15))
        assertEquals(0xEF.toByte(), rewritten[15])
        assertArrayEquals(original.copyOfRange(0, 11), rewritten.copyOfRange(0, 11))
        assertEquals(original.size, rewritten.size)
        assertNotEquals(
            original.toList().subList(11, 15),
            rewritten.toList().subList(11, 15)
        )
    }

    @Test
    fun theRewrittenFrameStillPassesItsChecksum() {
        ParameterAddress.FOREARM_LED.confirm(short)
        val original = ledOnFrame()
        assertTrue(crcIsValid(original))

        val rewritten = LedProfileHash
            .retargeted(profileOf(original), ParameterAddress.FOREARM_LED)
            .frames
            .first()

        assertTrue(crcIsValid(rewritten))
    }

    @Test
    fun retargetsInsideTheWrapperTheLedProfilesActuallyUse() {
        ParameterAddress.FOREARM_LED.confirm(short)
        val wrapped = Profiles.wrapFrame(ledOnFrame())

        val rewritten = LedProfileHash
            .retargeted(profileOf(wrapped), ParameterAddress.FOREARM_LED)
            .frames
            .first()

        assertEquals(wrapped.size, rewritten.size)
        assertArrayEquals(wrapped.copyOfRange(0, 8), rewritten.copyOfRange(0, 8))
        assertArrayEquals(short, rewritten.copyOfRange(8 + 11, 8 + 15))
        assertEquals(0xEF.toByte(), rewritten[8 + 15])
        assertTrue(crcIsValid(rewritten, innerStart = 8))
    }

    @Test
    fun framesThatDoNotCarryThisParameterAreUntouched() {
        ParameterAddress.FOREARM_LED.confirm(short)
        val unrelated = ledOnFrame(ParameterHash.of("g_config.gps_cfg.gps_enable"))

        val result = LedProfileHash
            .retargeted(profileOf(unrelated), ParameterAddress.FOREARM_LED)
            .frames
            .first()

        assertArrayEquals(unrelated, result)
    }

    @Test
    fun aReadbackConfirmsTheSpellingThatTheWriteThenUses() {
        val reply = byteArrayOf(0) + short + 0xEF.toByte()

        val readback = LedReadbackProtocol.parse(reply)

        assertEquals(LedState.ON, readback?.state)
        assertArrayEquals(short, ParameterAddress.FOREARM_LED.preferred())
    }

    @Test
    fun aReplyForSomeOtherParameterIsNotAccepted() {
        val reply = byteArrayOf(0) + ParameterHash.of("g_config.gps_cfg.gps_enable") + 0xEF.toByte()

        assertEquals(null, LedReadbackProtocol.parse(reply))
        assertArrayEquals(canonical, ParameterAddress.FOREARM_LED.preferred())
    }
}
