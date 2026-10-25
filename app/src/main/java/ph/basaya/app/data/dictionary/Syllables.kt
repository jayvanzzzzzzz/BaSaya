package ph.basaya.app.data.dictionary

import java.text.Normalizer

/**
 * Pantig: splits a Filipino word into syllables for display, "arkipelago" → "ar·ki·pe·la·go".
 * The same rules as the website's js/syllables.js, so keep the two in sync.
 *
 * Between two vowels:
 *  - none:  the vowels split                         (ka·in)
 *  - one:   it starts the next syllable              (ba·hay)
 *  - two:   one on each side                         (ak·lat, bun·dok)
 *  - three: two stay, one moves, unless the last two are a cluster like "tr" (sen·tro, es·kwe·la)
 *  - four+: two and two                              (eks·tra)
 * "ng" is one letter, consonants before the first vowel stay together (tra·ba·ho), and
 * hyphens and spaces always end a syllable (pag-i·big). Only a guess: a teacher's
 * `syllables` on the dictionary word wins (Dictionary.Entry.syllables).
 */
object Syllables {

    const val DOT = "·"

    private const val VOWELS = "aeiou"
    private const val CLUSTER_ENDS = "lrwy"
    private val MARKS = Regex("\\p{M}+")
    private val NON_LETTERS = Regex("[^\\p{L}]+")

    private class Unit(val text: String, val letter: String) {
        val vowel get() = letter in VOWELS.map(Char::toString)
    }

    private fun base(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    private fun isCluster(first: Unit, second: Unit) =
        first.letter != "ng" && (second.letter in CLUSTER_ENDS.map(Char::toString) || first.letter + second.letter == "ts")

    /** One run of letters (no hyphen or space) → its syllables. */
    private fun splitRun(run: String): List<String> {
        val chars = run.codePoints().toArray().map { String(Character.toChars(it)) }
        val units = mutableListOf<Unit>()
        var i = 0
        while (i < chars.size) {
            if (base(chars[i]) == "n" && i + 1 < chars.size && base(chars[i + 1]) == "g") {
                units += Unit(chars[i] + chars[i + 1], "ng")
                i += 2
            } else {
                units += Unit(chars[i], base(chars[i]))
                i++
            }
        }
        val vowels = units.indices.filter { units[it].vowel }
        if (vowels.size < 2) return listOf(run)

        val starts = (1 until vowels.size).map { v ->
            val from = vowels[v - 1] + 1
            when (val between = vowels[v] - from) {
                0, 1 -> from
                2 -> from + 1
                3 -> if (isCluster(units[from + 1], units[from + 2])) from + 1 else from + 2
                else -> if (between > 3) from + 2 else from
            }
        }
        var at = 0
        return (starts + units.size).map { cut ->
            units.subList(at, cut).joinToString("") { it.text }.also { at = cut }
        }
    }

    /** "bahay" → "ba·hay", "pag-ibig" → "pag-i·big". Keeps the word's own case and accent marks. */
    fun split(word: String): String {
        val text = Normalizer.normalize(word.trim(), Normalizer.Form.NFC)
        val out = StringBuilder()
        var last = 0
        for (gap in NON_LETTERS.findAll(text)) {
            if (gap.range.first > last) out.append(splitRun(text.substring(last, gap.range.first)).joinToString(DOT))
            out.append(gap.value)
            last = gap.range.last + 1
        }
        if (last < text.length) out.append(splitRun(text.substring(last)).joinToString(DOT))
        return out.toString()
    }

    /** True when there is more than one syllable to show. */
    fun hasBreaks(syllables: String) = DOT in syllables
}
