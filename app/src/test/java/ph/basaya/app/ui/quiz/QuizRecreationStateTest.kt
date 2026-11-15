package ph.basaya.app.ui.quiz

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import ph.basaya.app.data.model.Quiz
import ph.basaya.app.data.model.QuizQuestion

class QuizRecreationStateTest {
    private val quiz = Quiz("lesson", "Quiz", List(3) {
        QuizQuestion("Question $it", listOf("A", "B", "C"), multiple = true, requiresServerValidation = true)
    })
    private fun state() = QuizRecreationState("lesson", "student", "class", "session", "attempt",
        QuizRecreationState.fingerprint(quiz), listOf(2, 0, 1),
        listOf(listOf(1, 2, 0), listOf(2, 0, 1), listOf(0, 2, 1)),
        0, setOf(0, 2), setOf(2), 1)
    private fun restore(state: QuizRecreationState = state(), answered: Set<Int> = setOf(2), practice: Boolean = false) =
        state.restore(quiz, "lesson", "student", "class", "session", "attempt", answered, practice)

    @Test fun savedStateRoundTripKeepsQuestionChoicesAndMultipleSelection() {
        val saved = Gson().fromJson(Gson().toJson(state()), QuizRecreationState::class.java)
        val restored = restore(saved)!!
        assertEquals(listOf(0, 1), restored.positions)
        assertEquals(listOf(2, 0, 1), restored.choiceOrders.first())
        assertEquals(setOf(0, 2), restored.selected)
        assertEquals(listOf(2, 1), restored.selected.map { restored.choiceOrders.first()[it] })
    }

    @Test fun answerAcceptedDuringRecreationAdvancesWithoutReusingSelection() {
        val restored = restore(answered = setOf(2, 0))!!
        assertEquals(listOf(1), restored.positions)
        assertTrue(restored.selected.isEmpty())
    }

    @Test fun allAcceptedAnswersProceedToFinalization() {
        assertTrue(restore(answered = setOf(0, 1, 2))!!.positions.isEmpty())
    }

    @Test fun savedAnswersCannotOverrideServerProgress() {
        assertEquals(listOf(2, 0, 1), restore(answered = emptySet())!!.positions)
    }

    @Test fun practiceKeepsLocallyAcceptedProgressAndScore() {
        val restored = restore(practice = true)!!
        assertEquals(listOf(0, 1), restored.positions)
        assertEquals(setOf(2), restored.completed)
        assertEquals(1, restored.practiceScore)
    }

    @Test fun anotherAccountClassSessionOrAttemptCannotRestoreSelections() {
        for (invalid in listOf(state().copy(owner = "other"), state().copy(classId = "other"),
            state().copy(session = "other"), state().copy(attemptId = "other"), state().copy(lessonId = "other"))) {
            assertNull(restore(invalid))
        }
    }

    @Test fun editedContentOrInvalidOrdersCannotRestoreSelections() {
        assertNull(restore(state().copy(contentHash = "old")))
        assertNull(restore(state().copy(positions = listOf(2, 0, 0))))
        assertNull(restore(state().copy(choiceOrders = List(3) { listOf(0, 0, 1) })))
        assertNull(restore(state().copy(positions = listOf(2, 0), choiceOrders = state().choiceOrders.take(2))))
    }
}
