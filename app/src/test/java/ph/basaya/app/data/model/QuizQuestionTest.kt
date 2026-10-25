package ph.basaya.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizQuestionTest {

    private val choices = listOf("aso", "bahay", "pusa")

    @Test
    fun olderQuizWithSingleAnswer() {
        val q = QuizQuestion("?", choices, answer = 1)
        assertEquals(setOf(1), q.correctAnswers)
        assertFalse(q.isMultiple)
    }

    @Test
    fun twoOrMoreAnswersMeansMultipleSelect() {
        val q = QuizQuestion("?", choices, answer = 0, answers = listOf(0, 2), multiple = true)
        assertEquals(setOf(0, 2), q.correctAnswers)
        assertTrue(q.isMultiple)
    }

    @Test
    fun oneCheckedAnswerStaysSingleEvenIfMarkedMultiple() {
        val q = QuizQuestion("?", choices, answer = 2, answers = listOf(2), multiple = true)
        assertFalse(q.isMultiple)
    }

    @Test
    fun multipleAnswerIsRightOnlyWhenAllCorrectChoicesArePicked() {
        val q = QuizQuestion("?", choices, answer = 0, answers = listOf(0, 2), multiple = true)
        // QuizActivity scores a question as right when the picked set equals correctAnswers.
        assertEquals(setOf(0, 2), q.correctAnswers)            // both picked → right
        assertNotEquals(setOf(0), q.correctAnswers)            // only one → wrong
        assertNotEquals(setOf(0, 1, 2), q.correctAnswers)      // extra wrong pick → wrong
    }
}
