package com.freefcc.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AircraftSerialQueryTest {

    private fun reply(status: Int, length: Int, body: ByteArray): ByteArray =
        byteArrayOf(status.toByte(), length.toByte(), (length shr 8).toByte()) + body

    @Test
    fun requestAsksTheFlightControllerForTheSerialField() {
        val frame = AircraftSerialProtocol.buildRequest()

        // 55 <len> <ver+len_hi> <crc8> <sender> <dst> <seq lo> <seq hi> <type> <set> <id> <payload>
        assertEquals(0x55.toByte(), frame[0])
        assertEquals(0x02.toByte(), frame[4])                       // sender: mobile app
        assertEquals(0x03.toByte(), frame[5])                       // dst: flight controller
        assertEquals(0x40.toByte(), frame[8])                       // request with ACK
        assertEquals(0x00.toByte(), frame[9])                       // cmd_set
        assertEquals(0x51.toByte(), frame[10])                      // cmd_id
        assertEquals(AircraftSerialProtocol.FIELD_AIRCRAFT_SERIAL.toByte(), frame[11])
        assertEquals(14, frame.size)                                // 13 + one selector byte
    }

    @Test
    fun parsesTheSerialFromALiveReply() {
        // Captured from Avata 360 on 2026-08-08: 00 14 00 then 20 ASCII bytes.
        val serial = "1581FA8JC264600B31QZ"
        val payload = reply(0x00, serial.length, serial.toByteArray(Charsets.US_ASCII))

        assertEquals(serial, AircraftSerialProtocol.parse(payload))
    }

    @Test
    fun rejectsRepliesThatCarryNoUsableSerial() {
        val serial = "1581FA8JC264600B31QZ".toByteArray(Charsets.US_ASCII)

        // Field refused: the aircraft answers 0xfd when the selector matches no field.
        assertEquals("", AircraftSerialProtocol.parse(reply(0xFD, 0, ByteArray(20))))
        // Length longer than the bytes actually present must not over-read.
        assertEquals("", AircraftSerialProtocol.parse(reply(0x00, 40, serial)))
        // A controller serial is not an aircraft serial and must not be stored as one.
        assertEquals("", AircraftSerialProtocol.parse(reply(0x00, 14, "6UZBL7H02101J6".toByteArray())))
        // Truncated and empty replies.
        assertEquals("", AircraftSerialProtocol.parse(byteArrayOf(0x00, 0x14)))
        assertEquals("", AircraftSerialProtocol.parse(ByteArray(0)))
        assertEquals("", AircraftSerialProtocol.parse(null))
    }

    @Test
    fun ignoresTrailingBytesBeyondTheDeclaredLength() {
        val serial = "1581FA8JC264600B31QZ"
        val payload = reply(
            0x00,
            serial.length,
            serial.toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00, 0x7F, 0x11)
        )

        assertEquals(serial, AircraftSerialProtocol.parse(payload))
    }

    private val aircraftA = "1581FA8JC264600B31QZ"
    private val aircraftB = "1581FB34C25CF0032AAG"

    @Test
    fun listeningIsSkippedOnlyWhenNothingIsLeftToLearn() {
        // Same aircraft, model already read: the listen would hold 40007 to
        // re-read what we have.
        assertEquals(
            false,
            AircraftIdentitySources.needsListen(aircraftA, aircraftA, "WA530")
        )
        // The model only ever arrives by listening.
        assertEquals(true, AircraftIdentitySources.needsListen(aircraftA, aircraftA, ""))
        // Nothing known: the listen is the fallback the query did not replace.
        assertEquals(true, AircraftIdentitySources.needsListen("", "", ""))
    }

    @Test
    fun anUnansweredCheckDoesNotFallBackToListeningWhenNothingIsMissing() {
        // Listening on every missed answer turned the two-minute check into a
        // minute-long burst: the window stayed open and retried every ten
        // seconds, holding the port through a full listen each time.
        assertEquals(false, AircraftIdentitySources.needsListen("", aircraftA, "WA530"))
        assertEquals(
            true,
            AircraftIdentitySources.verificationSettled("", aircraftA, "WA530")
        )
    }

    @Test
    fun anUnansweredCheckStillListensWhileSomethingIsMissing() {
        // Half an identity is still discovery, and discovery needs the listen.
        assertEquals(true, AircraftIdentitySources.needsListen("", aircraftA, ""))
        assertEquals(false, AircraftIdentitySources.verificationSettled("", aircraftA, ""))
        assertEquals(true, AircraftIdentitySources.needsListen("", "", "WA530"))
        assertEquals(false, AircraftIdentitySources.verificationSettled("", "", "WA530"))
    }

    @Test
    fun anAnsweredCheckIsNeverSettledByThisRule() {
        // A serial in hand is handled by the swap path, not by giving up.
        assertEquals(
            false,
            AircraftIdentitySources.verificationSettled(aircraftA, aircraftA, "WA530")
        )
    }

    @Test
    fun aDifferentAircraftIsListenedToEvenThoughSomeModelIsStored() {
        // The stored model belongs to the aircraft that was just unplugged.
        // Counting it as known would file the new serial under the old model.
        assertEquals(
            true,
            AircraftIdentitySources.needsListen(aircraftB, aircraftA, "WA341")
        )
    }

    @Test
    fun theShortAndLongFormsOfOneSerialAreNotTreatedAsTwoAircraft() {
        val shortForm = aircraftA.removePrefix("1581")

        assertEquals(
            false,
            AircraftIdentitySources.needsListen(shortForm, aircraftA, "WA530")
        )
    }

    @Test
    fun theQueriedSerialOutranksTheListenedOneAndTheModelSurvives() {
        val listened = AircraftLinkIdentity(
            serial = aircraftA,
            model = AircraftModelIdentity("WA530", "DJI Avata 360")
        )

        val merged = AircraftIdentitySources.merge(aircraftA, listened)

        assertEquals(aircraftA, merged.serial)
        assertEquals("WA530", merged.model?.modelCode)
    }

    @Test
    fun aModelHeardFromAnotherAircraftIsNotPinnedToTheOneWeAsked() {
        // 40007 still carries frames from the aircraft that was unplugged.
        val listened = AircraftLinkIdentity(
            serial = aircraftA,
            model = AircraftModelIdentity("WA530", "DJI Avata 360")
        )

        val merged = AircraftIdentitySources.merge(aircraftB, listened)

        assertEquals(aircraftB, merged.serial)
        assertEquals(null, merged.model)
    }

    @Test
    fun anUnattributedModelStillCountsForTheAircraftWeAsked() {
        // The listen saw a model frame but no serial to argue with.
        val listened = AircraftLinkIdentity(
            serial = "",
            model = AircraftModelIdentity("WA530", "DJI Avata 360")
        )

        assertEquals("WA530", AircraftIdentitySources.merge(aircraftB, listened).model?.modelCode)
    }

    @Test
    fun withoutAnAnswerTheListenedIdentityIsUsedWhole() {
        val listened = AircraftLinkIdentity(
            serial = "1581FA8JC264600B31QZ",
            model = AircraftModelIdentity("WA530", "DJI Avata 360")
        )

        val merged = AircraftIdentitySources.merge("", listened)

        assertEquals(listened.serial, merged.serial)
        assertEquals("WA530", merged.model?.modelCode)
    }

    @Test
    fun skippingTheListenLeavesNoModelRatherThanAStaleOne() {
        val merged = AircraftIdentitySources.merge("1581FA8JC264600B31QZ", null)

        assertEquals("1581FA8JC264600B31QZ", merged.serial)
        assertEquals(null, merged.model)
        // Nothing observed at all still means nothing claimed.
        assertEquals("", AircraftIdentitySources.merge("", null).serial)
    }

    @Test
    fun selectorIsCarriedVerbatimSoOtherIdentityFieldsStayReachable() {
        val frame = AircraftSerialProtocol.buildRequest(field = 0x0C)

        assertArrayEquals(byteArrayOf(0x0C), frame.copyOfRange(11, 12))
    }
}
