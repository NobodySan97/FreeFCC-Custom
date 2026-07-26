package com.freefcc.app

import java.util.Locale

/** A product code and the commercial name that belongs to it. */
internal data class AircraftModelMatch(
    val code: String,
    val name: String
)

/**
 * Known DJI aircraft product codes and the names DJI Fly prints for them.
 *
 * The catalog is read in both directions. DJI Fly shows the code and the
 * commercial name on screen, so the accessibility service can recognise both
 * without opening a DUML port; the passive DUML path uses the same table to
 * name a code that arrived without a `03:34` string.
 */
internal object AircraftModelCatalog {

    /**
     * WM240/245/246/247 are the Mavic 2 platform. The exact variant is not
     * distinguishable from the code alone here, so all four share one name and
     * the reverse lookup deliberately refuses to guess a code from it.
     */
    private val NAME_BY_CODE = mapOf(
        "WA233" to "DJI Air 3",
        "WA234" to "DJI Air 3S",
        "WA341" to "DJI Mavic 4 Pro",
        "WA140" to "DJI Mini 4 Pro",
        "WA150" to "DJI Mini 5 Pro",
        "WA141" to "DJI Flip",
        "WA520" to "DJI Avata 2",
        "WA521" to "DJI Neo",
        "WM162" to "DJI Mini 3 Pro",
        "WM169" to "DJI Avata",
        "WM260" to "DJI Mavic 3",
        "WM2605" to "DJI Mavic 3 Classic",
        "WM261" to "DJI Mavic 3 Pro",
        "WM265E" to "DJI Mavic 3E",
        "WM265M" to "DJI Mavic 3M",
        "WM265T" to "DJI Mavic 3T",
        "WM240" to "DJI Mavic 2",
        "WM245" to "DJI Mavic 2",
        "WM246" to "DJI Mavic 2",
        "WM247" to "DJI Mavic 2"
    )

    private val CODE_REGEX = Regex("(?<![0-9A-Z])W[AM][0-9]{3}[0-9A-Z]?(?![0-9A-Z])")
    private val WHITESPACE = Regex("\\s+")

    /** Names that map to exactly one code; ambiguous ones are dropped. */
    private val CODE_BY_NAME: Map<String, String> = run {
        val byName = mutableMapOf<String, String>()
        val ambiguous = mutableSetOf<String>()
        NAME_BY_CODE.forEach { (code, name) ->
            val key = normalize(name)
            if (byName.put(key, code) != null) ambiguous += key
        }
        ambiguous.forEach(byName::remove)
        byName
    }

    /**
     * Search strings ordered longest-first so `DJI Air 3S` wins over
     * `DJI Air 3`. The `DJI`-less form is only searched when it is long enough
     * to be unambiguous on a busy screen — `Neo` or `Flip` alone are not.
     */
    private val ALIASES: List<Pair<String, String>> = NAME_BY_CODE.values
        .distinct()
        .flatMap { name ->
            val full = normalize(name)
            val short = full.removePrefix("DJI ")
            buildList {
                add(full to name)
                if (short.length >= 6 && short != full) add(short to name)
            }
        }
        .sortedByDescending { it.first.length }

    fun nameForCode(code: String): String =
        NAME_BY_CODE[code.trim().uppercase(Locale.US)].orEmpty()

    /**
     * Finds the aircraft identity in text taken off a DJI app screen. The code
     * is preferred from the text itself; the catalog only fills in what the
     * screen did not spell out.
     */
    fun findInText(text: CharSequence): AircraftModelMatch? {
        val haystack = normalize(text)
        if (haystack.isEmpty()) return null

        val name = ALIASES.firstOrNull { containsWord(haystack, it.first) }?.second.orEmpty()
        val code = CODE_REGEX.find(haystack)?.value
            ?: CODE_BY_NAME[normalize(name)].orEmpty()

        return if (code.isEmpty() && name.isEmpty()) {
            null
        } else {
            AircraftModelMatch(code, name.ifEmpty { nameForCode(code) })
        }
    }

    private fun normalize(value: CharSequence): String =
        value.toString()
            .replace('\u00A0', ' ')
            .uppercase(Locale.US)
            .replace(WHITESPACE, " ")
            .trim()

    /** Substring match that refuses to fire inside a longer alphanumeric run. */
    private fun containsWord(haystack: String, needle: String): Boolean {
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return false
            val before = haystack.getOrNull(at - 1)
            val after = haystack.getOrNull(at + needle.length)
            if (!isWordChar(before) && !isWordChar(after)) return true
            from = at + 1
        }
    }

    private fun isWordChar(value: Char?): Boolean =
        value != null && (value.isLetterOrDigit())
}
