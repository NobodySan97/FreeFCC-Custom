package com.freefcc.app

/**
 * Rewrites the parameter hash inside an already-built LED profile frame.
 *
 * `led_on.json` and `led_off.json` carry the canonical hash as a literal
 * (`a259ceed` followed by the value byte). That is correct everywhere the
 * firmware still calls the parameter `g_config.misc_cfg.forearm_lamp_ctrl`,
 * and wrong on a firmware that only knows the short `forearm_led_ctrl` — the
 * write there addresses nothing and reports success, which is how a dead
 * switch looks from the outside.
 *
 * The profile is left untouched until a readback has proven which spelling the
 * aircraft answers to, so an aircraft we know nothing about keeps behaving
 * exactly as before.
 */
internal object LedProfileHash {

    private const val DUML_HEADER_SIZE = 11
    private const val CRC16_SIZE = 2
    private const val HASH_SIZE = 4

    /**
     * Profiles marked `"wrapper": true` — which both LED profiles are — reach
     * us already wrapped by [Profiles.load], so the DUML frame does not start
     * at offset zero and its CRC16 must not be computed from there.
     */
    private val WRAPPER_MAGIC = byteArrayOf(0x55, 0xCC.toByte(), 0x30, 0x75)

    /** Magic plus the 4-byte little-endian length of the frame it carries. */
    private const val WRAPPER_SIZE = 8

    fun retargeted(profile: Profiles.Profile, address: ParameterAddress): Profiles.Profile {
        val canonical = address.candidates.first()
        val preferred = address.preferred()
        if (preferred.contentEquals(canonical)) return profile

        return profile.copy(
            frames = profile.frames.map { frame -> retarget(frame, canonical, preferred) }
        )
    }

    private fun retarget(frame: ByteArray, canonical: ByteArray, preferred: ByteArray): ByteArray {
        val innerStart = if (isWrapped(frame)) WRAPPER_SIZE else 0
        val payloadStart = innerStart + DUML_HEADER_SIZE
        val payloadEnd = frame.size - CRC16_SIZE
        if (payloadEnd - payloadStart < HASH_SIZE) return frame

        val carriesCanonical = (0 until HASH_SIZE).all { i ->
            frame[payloadStart + i] == canonical[i]
        }
        if (!carriesCanonical) return frame

        val rewritten = frame.copyOf()
        preferred.copyInto(rewritten, payloadStart, 0, HASH_SIZE)

        // Only the payload changed, so the length byte and its CRC8 still hold.
        // The trailing CRC16 covers the DUML frame, which starts after the
        // wrapper when there is one — computing it from offset zero would
        // checksum the wrapper too and the aircraft would drop the frame.
        val crc = DumlBuilder.crc16(
            rewritten,
            innerStart,
            rewritten.size - CRC16_SIZE - innerStart
        )
        rewritten[rewritten.size - 2] = crc.toByte()
        rewritten[rewritten.size - 1] = (crc shr 8).toByte()
        return rewritten
    }

    private fun isWrapped(frame: ByteArray): Boolean =
        frame.size > WRAPPER_SIZE + DUML_HEADER_SIZE &&
            WRAPPER_MAGIC.indices.all { frame[it] == WRAPPER_MAGIC[it] }
}
