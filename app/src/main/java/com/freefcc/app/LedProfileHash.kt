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

    private const val HEADER_SIZE = 11
    private const val CRC16_SIZE = 2
    private const val HASH_SIZE = 4

    fun retargeted(profile: Profiles.Profile, address: ParameterAddress): Profiles.Profile {
        val canonical = address.candidates.first()
        val preferred = address.preferred()
        if (preferred.contentEquals(canonical)) return profile

        return profile.copy(
            frames = profile.frames.map { frame -> retarget(frame, canonical, preferred) }
        )
    }

    private fun retarget(frame: ByteArray, canonical: ByteArray, preferred: ByteArray): ByteArray {
        val payloadStart = HEADER_SIZE
        val payloadEnd = frame.size - CRC16_SIZE
        if (payloadEnd - payloadStart < HASH_SIZE) return frame

        val carriesCanonical = (0 until HASH_SIZE).all { i ->
            frame[payloadStart + i] == canonical[i]
        }
        if (!carriesCanonical) return frame

        val rewritten = frame.copyOf()
        preferred.copyInto(rewritten, payloadStart, 0, HASH_SIZE)

        // Only the payload changed, so the length byte and its CRC8 still hold;
        // the trailing CRC16 covers the payload and must be recomputed.
        val crc = DumlBuilder.crc16(rewritten, 0, rewritten.size - CRC16_SIZE)
        rewritten[rewritten.size - 2] = crc.toByte()
        rewritten[rewritten.size - 1] = (crc shr 8).toByte()
        return rewritten
    }
}
