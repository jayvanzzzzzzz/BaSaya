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
