package com.freefcc.app

/**
 * One aircraft puts its serial on the bus in two spellings. Live on an Avata
 * 360, `51:14` carried `1581FA8JC264600B31QZ` and `03:44` carried
 * `FA8JC264600B31QZ` minutes apart — the same number, the second one without
 * its `1581` factory prefix. Compared as plain strings they look like two
 * aircraft: the stored S/N flips between them, every flip reads as a swap, and
 * statistics count one drone twice.
 */
internal object AircraftSerialForms {
    /** Length of the short spelling; anything shorter is not an S/N tail. */
    private const val TAIL_LENGTH = 16

    /** True when the two spellings can only belong to the same aircraft. */
    fun sameAircraft(first: String, second: String): Boolean {
        if (first.isEmpty() || second.isEmpty()) return false
        if (first == second) return true
        val longer = if (first.length >= second.length) first else second
        val shorter = if (longer === first) second else first
        return shorter.length >= TAIL_LENGTH && longer.endsWith(shorter)
    }

    /** The fuller of two spellings; the prefixed form is the one worth storing. */
    fun preferred(first: String, second: String): String =
        if (second.length > first.length) second else first
}
