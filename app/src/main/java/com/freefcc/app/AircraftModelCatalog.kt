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

    /**
     * A `DJI …` product name of up to three words. The name is taken verbatim,
     * so an aircraft the catalog has never heard of still gets its real name.
     */
    private val PRODUCT_NAME_REGEX =
        Regex("DJI(?: [A-Za-z0-9][A-Za-z0-9+]{0,11}){1,3}", RegexOption.IGNORE_CASE)

    /**
     * First word after `DJI` for products that are not aircraft. Without this
     * the app's own name, the store banner or a paired accessory would be read
     * as the connected drone.
     */
    private val NON_AIRCRAFT_WORDS = setOf(
        "FLY", "GO", "GO4", "PILOT", "ASSISTANT", "MIMO", "STORE", "ACCOUNT",
        "CARE", "FORUM", "ACADEMY", "SUPPORT", "SERVICE", "VIRTUAL", "GOGGLES",
        "MOTION", "RC", "RM", "GL", "MIC", "OSMO", "POCKET", "ACTION", "POWER",
        "TERRA", "CELLULAR", "TRANSMISSION", "SDK"
    )

    fun nameForCode(code: String): String =
        NAME_BY_CODE[code.trim().uppercase(Locale.US)].orEmpty()

    /**
     * The code that may stay stored next to [name]. Name and code are one
     * identity, so a code read for another aircraft must not outlive it: an
     * Avata 360 named on screen keeps no WM169 from the previous session.
     * Returns an empty string when the stored code has to go.
     */
    fun codeFor(
        name: String,
        observedCode: String,
        storedName: String,
        storedCode: String
    ): String {
        if (observedCode.isNotEmpty()) return observedCode
        if (name.isEmpty() || storedCode.isEmpty()) return storedCode
        val catalogName = nameForCode(storedCode)
        val contradicted = catalogName.isNotEmpty() && !catalogName.equals(name, ignoreCase = true)
        return if (contradicted || !name.equals(storedName, ignoreCase = true)) "" else storedCode
    }

    /**
     * Finds the aircraft identity in the labels of a DJI app screen.
     *
     * The name printed on screen wins and is kept verbatim — the catalog is
     * only consulted when the screen shows a code without a name, or to turn a
     * known name back into its code.
     */
    fun findOnScreen(texts: Collection<CharSequence>): AircraftModelMatch? {
        val labels = texts.map(::flatten).filter(String::isNotEmpty)
        if (labels.isEmpty()) return null

        val haystack = labels.joinToString(" | ").uppercase(Locale.US)
        val code = CODE_REGEX.find(haystack)?.value.orEmpty()
        val name = screenName(labels)
            ?: ALIASES.firstOrNull { containsWord(haystack, it.first) }?.second
            ?: nameForCode(code)

        val resolvedCode = code.ifEmpty { CODE_BY_NAME[normalize(name)].orEmpty() }
        return if (resolvedCode.isEmpty() && name.isEmpty()) {
            null
        } else {
            AircraftModelMatch(resolvedCode, name)
        }
    }

    /**
     * The name as the screen spells it. A label that is nothing but a product
     * name is the device caption; otherwise the name is only trusted when it
     * sits in the same label as a product code.
     */
    private fun screenName(labels: List<String>): String? {
        labels.forEach { label ->
            if (isAircraftName(label)) return label
        }
        labels.forEach { label ->
            if (!CODE_REGEX.containsMatchIn(label.uppercase(Locale.US))) return@forEach
            PRODUCT_NAME_REGEX.find(label)?.value?.let { if (isAircraftName(it)) return it }
        }
        // Last: a product name embedded in a sentence, e.g. "Connected to
        // DJI Avata 360". Still filtered by the non-aircraft word list.
        labels.forEach { label ->
            PRODUCT_NAME_REGEX.find(label)?.value?.let { if (isAircraftName(it)) return it }
        }
        return null
    }

    private fun isAircraftName(value: String): Boolean {
        if (value.length > 28 || !PRODUCT_NAME_REGEX.matches(value)) return false
        val words = value.split(' ')
        return words.size >= 2 && words[1].uppercase(Locale.US) !in NON_AIRCRAFT_WORDS
    }

    /** Collapses screen whitespace but keeps the text exactly as printed. */
    private fun flatten(value: CharSequence): String =
        value.toString()
            .replace('\u00A0', ' ')
            .replace(WHITESPACE, " ")
            .trim()

    private fun normalize(value: CharSequence): String =
        flatten(value).uppercase(Locale.US)

    /**
     * Substring match that refuses to fire inside a longer alphanumeric run,
     * and also refuses when a number follows: `DJI Avata` must not be reported
     * for a screen that says `DJI Avata 360`.
     */
    private fun containsWord(haystack: String, needle: String): Boolean {
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return false
            val end = at + needle.length
            val before = haystack.getOrNull(at - 1)
            val after = haystack.getOrNull(end)
            val extendedByNumber =
                after == ' ' && haystack.getOrNull(end + 1)?.isDigit() == true
            if (!isWordChar(before) && !isWordChar(after) && !extendedByNumber) return true
            from = at + 1
        }
    }

    private fun isWordChar(value: Char?): Boolean =
        value != null && (value.isLetterOrDigit())
}
