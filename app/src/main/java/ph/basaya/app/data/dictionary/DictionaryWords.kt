package ph.basaya.app.data.dictionary

import java.text.Normalizer

/**
 * Word helpers for the Diksiyonaryo, the same as the website's js/dictionaryWords.js.
 * Keep the two in sync: the search key must match the Firestore doc ids the
 * website's teacher page writes.
 */
object DictionaryWords {

    private val MARKS = Regex("\\p{M}+")
    private val SPACES = Regex("\\s+")

    /** Search key: "Báhay " → "bahay". Accent marks and case don't matter when looking up. */
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace(SPACES, " ")
            .trim()

    // Longest first, so "nakaka" is tried before "naka" and "na".
    private val PREFIXES = listOf(
        "nakakapag", "nakaka", "pinakama", "pinaka", "magpa", "nagpa", "pagpa", "ipinag", "ipag", "ipina", "ipa",
        "pagka", "naka", "maka", "mapa", "napa", "mag", "nag", "pag", "mang", "nang", "pang", "mam", "nam", "pam",
        "man", "nan", "pan", "ma", "na", "ka", "pa", "um", "in", "i",
    )
    private val SUFFIXES = listOf("han", "hin", "nan", "nin", "an", "in")
    private val VOWEL = Regex("[aeiou]")
    private val INFIX = Regex("^([^aeiou])(um|in)(.+)$")
    private val REDUPLICATION = Regex("^([^aeiou]?[aeiou])\\1(.+)$")

    /** Drops one affix, infix or repeated syllable at a time: kumakain → kakain → kain. */
    private fun strippings(word: String): List<String> {
        val out = mutableListOf<String>()
        PREFIXES.filter { word.startsWith(it) }.forEach { out += word.substring(it.length) }
        SUFFIXES.filter { word.endsWith(it) }.forEach { out += word.dropLast(it.length) }
        // -um- / -in- after the first consonant: sumulat, sinulat → sulat.
        INFIX.find(word)?.let { out += it.groupValues[1] + it.groupValues[3] }
        // Repeated first syllable: kakain → kain, susulat → sulat.
        REDUPLICATION.find(word)?.let { out += it.groupValues[1] + it.groupValues[2] }
        // Words joined by an affix sometimes keep a hyphen: mag-aral → aral.
        if ('-' in word) out += word.substringAfter('-')
        return out.map { it.removePrefix("-") }.filter { it.length >= 3 && VOWEL.containsMatchIn(it) }
    }

    /** Possible root words of an inflected word, closest first. Only a guess. */
    fun guessRoots(value: String, depth: Int = 3): List<String> {
        val start = normalize(value)
        val seen = mutableSetOf(start)
        val found = mutableListOf<String>()
        var level = listOf(start)
        repeat(depth) {
            val next = mutableListOf<String>()
            for (word in level) {
                for (root in strippings(word)) {
                    if (seen.add(root)) {
                        found += root
                        next += root
                    }
                }
            }
            level = next
        }
        return found
    }
}
