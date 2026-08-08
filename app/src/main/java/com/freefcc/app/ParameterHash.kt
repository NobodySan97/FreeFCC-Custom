package com.freefcc.app

/**
 * Hash a flight-controller parameter is addressed by in `03:F8` and `03:F9`.
 *
 * Extracted from `compute_hash_value_by_name` in DJI firmware and recorded in
 * `docs/FLYC_PARAM_TABLE.md`: fold `name + "_0"` with
 * `h = ((h << 8) | byte) mod 0xffffffb`, send little-endian. It reproduces
 * every hash we have seen on the wire — see the test.
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
     *
     * Every candidate names the same parameter, so a value left over from a
     * previous aircraft cannot address something else — at worst the firmware
     * does not know that spelling and the write resolves to nothing, and the
     * next read replaces it.
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

    /** Test seam — forgets what an aircraft proved. */
    fun forgetConfirmed() {
        confirmed = null
    }

    companion object {
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
