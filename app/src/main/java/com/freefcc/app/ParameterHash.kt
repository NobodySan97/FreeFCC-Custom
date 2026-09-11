package com.freefcc.app

/**
 * Hash a flight-controller parameter is addressed by in `03:F8` and `03:F9`.
 *
 * Extracted from `compute_hash_value_by_name` in DJI firmware: fold `name + "_0"` with
 * `h = ((h << 8) | byte) mod 0xfffffffb`, send little-endian.
 *
 * The hash is a function of the name and nothing else, so the same name gives
 * the same hash on every aircraft. What varies is which name a firmware
 * actually carries: Avata 360, Air 3S, Air 3, Flip, Mini 4 Pro, Neo and Neo 2
 * all report `forearm_led_ctrl|g_config.misc_cfg.forearm_lamp_ctrl`, while on
 * Lito X1 750 of 875 names lost the `g_config.*` half — including the LED and
 * GPS ones. That is why the old hashes find nothing there, and why parameters
 * are addressed here by a list of candidate names rather than one constant.
 */
internal object ParameterHash {

    private const val MODULUS = 0xFFFFFFFBL

    /** Little-endian wire bytes of the hash of [name]. */
    fun of(name: String): ByteArray {
        var accumulator = 0L
        for (byte in (name + "_0").toByteArray(Charsets.US_ASCII)) {
            accumulator = ((accumulator shl 8) or (byte.toLong() and 0xFF)) % MODULUS
        }
        return byteArrayOf(
            accumulator.toByte(),
            (accumulator ushr 8).toByte(),
            (accumulator ushr 16).toByte(),
            (accumulator ushr 24).toByte()
        )
    }
}

/**
 * The names one parameter is known by, most canonical first.
 *
 * A firmware answers `03:F8` for the name it actually carries and returns a
 * bare status byte for anything else, so the candidates are tried in order and
 * the one that answers is the one that exists on this aircraft.
 */
internal class ParameterAddress(vararg names: String) {

    val candidates: List<ByteArray> = names.map(ParameterHash::of)

    /**
     * The candidate this aircraft answered to, once a read has proven one.
     */
    @Volatile
    private var confirmed: ByteArray? = null

    /** True when [hash] is one of this parameter's candidate hashes. */
    fun matches(hash: ByteArray): Boolean =
        candidates.any { it.contentEquals(hash) }

    /** Records the candidate an aircraft actually answered to. */
    fun confirm(hash: ByteArray) {
        if (matches(hash)) confirmed = hash.copyOf()
    }

    /** Candidate to write with: the proven one, else the canonical name. */
    fun preferred(): ByteArray = confirmed ?: candidates.first()

    /** True once a read has proven which name this aircraft answers to. */
    val isConfirmed: Boolean
        get() = confirmed != null

    /**
     * Forgets what an aircraft proved. Called when the aircraft changes: the
     * confirmation belongs to the one that answered, and writing to a name the
     * new aircraft may not have is a switch that silently does nothing.
     */
    fun forgetConfirmed() {
        confirmed = null
    }

    /**
     * The spellings worth sending on one attempt.
     */
    fun spellingsToTry(): List<ByteArray> =
        if (isConfirmed) listOf(preferred()) else candidates

    companion object {
        /**
         * Reads a hash-addressed parameter, asking under each name it may go
         * by until one answers, and leaves the answering name confirmed for
         * the write that follows.
         */
        fun <T> read(
            transport: DumlTransport,
            address: ParameterAddress,
            readWindowMs: Int,
            buildRequest: (ByteArray) -> ByteArray,
            parse: (ByteArray?) -> T?,
            hashes: List<ByteArray> = address.spellingsToTry()
        ): T? {
            for (parameterHash in hashes) {
                val request = buildRequest(parameterHash)
                val exchange = transport.sendAndReceiveRaw(
                    frame = request,
                    wireFrame = Profiles.wrapFrame(request),
                    readWindowMs = readWindowMs,
                    port = DumlTransport.PORT_LED,
                    autoDetectPort = false
                )
                parse(exchange.validatedPayload)?.let { return it }
            }
            return null
        }

        /** Drops every parameter's confirmation, for an aircraft that changed. */
        fun forgetAllConfirmed() {
            FOREARM_LED.forgetConfirmed()
            GPS_ENABLE.forgetConfirmed()
        }

        /** Front arm LEDs. Short form confirmed present on Lito X1 firmware 03.07. */
        val FOREARM_LED = ParameterAddress(
            "g_config.misc_cfg.forearm_lamp_ctrl",
            "forearm_led_ctrl"
        )

        /** Master GNSS switch. */
        val GPS_ENABLE = ParameterAddress(
            "g_config.gps_cfg.gps_enable",
            "gps_enable"
        )
    }
}
