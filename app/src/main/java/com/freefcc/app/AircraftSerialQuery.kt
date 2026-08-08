package com.freefcc.app

/**
 * Asks the aircraft for its serial instead of waiting for one to appear.
 *
 * The passive probe holds `40007` open for the whole listen window and hopes
 * the identity broadcast lands inside it. That window is shared with the FPV
 * mirror, so holding it costs the DJI Fly link, and a miss costs the full
 * window again. `00:51` replaces the wait with one request and one reply: the
 * socket closes as soon as the answer arrives.
 *
 * Confirmed live on 2026-08-08 against Avata 360 (`wa530`) through the RC 2
 * proxy on `40007`. The payload is a single byte selecting which identity
 * field to return; the observed fields were:
 *
 * | selector | reply |
 * |---|---|
 * | `0x04`, `0x06` | aircraft serial, `00 14 00` + 20 ASCII bytes |
 * | `0x01` | serial behind four extra bytes (`00 16 20 08`) |
 * | `0x0c`, `0x0d` | 16-byte device id |
 * | `0x00`, `0xff` | status `0xfd`, no field |
 *
 * Only `0x04` is used here — it returns the serial with no framing to strip.
 */
internal object AircraftSerialProtocol {

    /** Payload selector that returns the bare aircraft serial. */
    const val FIELD_AIRCRAFT_SERIAL = 0x04

    /** Reply header: `status:u8` then the field length as `u16` little-endian. */
    private const val HEADER_SIZE = 3
    private const val STATUS_OK = 0

    fun buildRequest(
        field: Int = FIELD_AIRCRAFT_SERIAL,
        builder: DumlBuilder = DumlBuilder()
    ): ByteArray = builder.buildFrame(
        DumlFrame(
            sender = 0x02,
            cmdType = 0x40,
            cmdSet = 0x00,
            cmdId = 0x51,
            dst = 0x03,
            payload = byteArrayOf(field.toByte())
        )
    )

    /**
     * Returns the serial carried by a `00:51` reply, or an empty string when the
     * reply is absent, refuses the field, or does not hold a full factory
     * serial. A short or unrecognised value is dropped rather than stored:
     * a wrong serial is worse than no serial, because it is cached and reported.
     */
    fun parse(payload: ByteArray?): String {
        if (payload == null || payload.size < HEADER_SIZE) return ""
        if ((payload[0].toInt() and 0xFF) != STATUS_OK) return ""

        val declaredLength = (payload[1].toInt() and 0xFF) or ((payload[2].toInt() and 0xFF) shl 8)
        if (declaredLength <= 0 || HEADER_SIZE + declaredLength > payload.size) return ""

        val serial = String(
            payload,
            HEADER_SIZE,
            declaredLength,
            Charsets.US_ASCII
        ).trim()
        return if (DumlTransport.isFullAircraftSerial(serial)) serial else ""
    }
}

/**
 * Combines what the aircraft answered with what listening on the bus adds.
 *
 * The query returns a serial and nothing else, while the passive listen is the
 * only place a model arrives over the bus. Keeping the two apart lets the
 * listen — and the port it holds — be skipped once neither is missing.
 */
internal object AircraftIdentitySources {

    /**
     * True when listening can still add something the query did not answer.
     *
     * A stored model counts only while it belongs to the aircraft that just
     * answered. Treating any stored model as known would pair a newly read
     * serial with the previous aircraft's model — the swap leaves the model
     * behind, the query returns the new serial, and nothing ever listens to
     * correct it.
     */
    fun needsListen(
        queriedSerial: String,
        storedSerial: String,
        storedModelCode: String
    ): Boolean {
        if (queriedSerial.isEmpty()) return true
        if (storedModelCode.isEmpty()) return true
        return !AircraftSerialForms.sameAircraft(queriedSerial, storedSerial)
    }

    /**
     * The query's serial wins; the model can only come from the listen.
     *
     * A model is taken only when the listen did not name a different aircraft.
     * Port 40007 still carries frames from the aircraft that was just
     * unplugged, and pairing those with the serial we asked for is how a
     * mismatched identity gets stored and reported.
     */
    fun merge(queriedSerial: String, listened: AircraftLinkIdentity?): AircraftLinkIdentity {
        val listenedSerial = listened?.serial.orEmpty()
        val listenedIsSameAircraft = queriedSerial.isEmpty() ||
            listenedSerial.isEmpty() ||
            AircraftSerialForms.sameAircraft(queriedSerial, listenedSerial)
        return AircraftLinkIdentity(
            serial = queriedSerial.ifEmpty { listenedSerial },
            model = listened?.model.takeIf { listenedIsSameAircraft }
        )
    }
}

/** Runs the `00:51` serial query over the LED port with bounded retries. */
internal object AircraftSerialQueryRunner {

    // One request is often lost on 40007 — the port carries the FPV mirror, and
    // a live run answered four times out of six. Retries are cheap because a
    // successful exchange returns as soon as the reply is matched, unlike the
    // passive probe which always waits out its whole window.
    const val DEFAULT_ATTEMPTS = 3

    /**
     * One attempt is enough where the caller already retries on its own beat.
     * The background window pays for every millisecond it holds 40007, and a
     * miss there costs a ten-second wait rather than a failed operation.
     */
    const val BACKGROUND_ATTEMPTS = 1

    private const val READ_WINDOW_MS = 600

    fun read(
        transport: DumlTransport = DumlTransport(),
        attempts: Int = DEFAULT_ATTEMPTS
    ): String {
        repeat(attempts) {
            val request = AircraftSerialProtocol.buildRequest()
            val exchange = transport.sendAndReceiveRaw(
                frame = request,
                wireFrame = Profiles.wrapFrame(request),
                readWindowMs = READ_WINDOW_MS,
                port = DumlTransport.PORT_LED,
                autoDetectPort = false
            )
            val serial = AircraftSerialProtocol.parse(exchange.validatedPayload)
            if (serial.isNotEmpty()) return serial
        }
        return ""
    }
}
