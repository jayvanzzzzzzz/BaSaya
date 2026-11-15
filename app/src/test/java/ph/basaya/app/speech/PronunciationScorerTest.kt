package ph.basaya.app.speech

import ph.basaya.app.speech.PronunciationScorer.ScoreBand
import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationScorerTest {

    private fun band(target: String, vararg heard: String) =
        PronunciationScorer.score(target, heard.toList(), null).scoreBand

    @Test
    fun exactWordIsGreat() {
        assertEquals(ScoreBand.GREAT, band("Bahay", "bahay"))
    }

    @Test
    fun opinyonAcceptsRecognizerSpelling() {
        for (heard in listOf("opinyon", "Opinion!", "ang opinion", "o pin yon", "opi nion")) {
            assertEquals(heard, ScoreBand.GREAT, band("opinyon", heard))
        }
        assertEquals(ScoreBand.GREAT, band("opinion", "opinyon"))
    }

    @Test
    fun opinyonStillRejectsDifferentWordsAndEndings() {
        for (heard in listOf("opinyong", "opisyon", "opinyan", "pinyon", "onion")) {
            assertEquals(heard, ScoreBand.TRY_AGAIN, band("opinyon", heard))
        }
    }

    @Test
    fun glideSpellingRulesApplyAcrossWordsInBothDirections() {
        val pairs = listOf(
            "opinyon" to "opiniyon", "leksyon" to "leksiyon",
            "leksyon" to "leksion", "aksyon" to "aksiyon",
            "pasyon" to "pasion", "misyon" to "mision",
            "dyaryo" to "diyaryo", "tyempo" to "tiempo",
            "kwento" to "kuwento", "kwarto" to "kuarto",
            "biskwit" to "biskuwit", "indibidwal" to "indibidual"
        )
        for ((target, heard) in pairs) {
            assertEquals("$target / $heard", ScoreBand.GREAT, band(target, heard))
            assertEquals("$heard / $target", ScoreBand.GREAT, band(heard, target))
        }
    }

    @Test
    fun multipleFragmentsInsideAPhraseAreJoinedWithoutSkippingWords() {
        assertEquals(ScoreBand.GREAT, band("opinyon", "ang o pin yon po"))
        assertEquals(ScoreBand.GREAT, band("leksyon", "ang lek si yon po"))
        assertEquals(ScoreBand.GREAT, band("kwento", "ang ku wen to po"))
        assertEquals(ScoreBand.TRY_AGAIN, band("opinyon", "o ibang pin yon"))
    }

    @Test
    fun phoneticTolerancePreservesConsonantsAndVowelHiatus() {
        for ((target, heard) in listOf(
            "misyon" to "nasyon", "pasyon" to "pasyonko",
            "kwento" to "kwenta", "bata" to "pata",
            "leon" to "leyon", "buan" to "ban"
        )) assertEquals("$target / $heard", ScoreBand.TRY_AGAIN, band(target, heard))
    }

    @Test
    fun punctuationCaseAndAccentsAreIgnored() {
        assertEquals(ScoreBand.GREAT, band("bahay", "Báhay."))
    }

    @Test
    fun targetInsideALongerPhraseIsFound() {
        assertEquals(ScoreBand.GREAT, band("bahay", "ang bahay"))
    }

    @Test
    fun wordSplitInTwoIsJoined() {
        assertEquals(ScoreBand.GREAT, band("bahay", "ba hay"))
    }

    @Test
    fun swappedTagalogVowelsCountAsSameSound() {
        assertEquals(ScoreBand.GREAT, band("puno", "punu"))
        assertEquals(ScoreBand.GREAT, band("babae", "babai"))
    }

    @Test
    fun extraLetterOnShortWordNeedsAnotherTry() {
        assertEquals(ScoreBand.TRY_AGAIN, band("mataba", "matabak"))
        assertEquals(ScoreBand.TRY_AGAIN, band("bata", "bataa"))
    }

    @Test
    fun longWordAllowsOneSlipAsOkay() {
        assertEquals(ScoreBand.OKAY, band("paaralan", "paaralang"))
        assertEquals(ScoreBand.TRY_AGAIN, band("paaralan", "paralang"))
    }

    @Test
    fun differentWordNeedsAnotherTry() {
        assertEquals(ScoreBand.TRY_AGAIN, band("bata", "pusa"))
    }

    @Test
    fun bestOfSeveralCandidatesIsUsed() {
        assertEquals(ScoreBand.GREAT, band("aso", "ako", "aso"))
    }

    @Test
    fun noCandidatesNeedsAnotherTry() {
        assertEquals(ScoreBand.TRY_AGAIN, band("aso"))
    }
}
