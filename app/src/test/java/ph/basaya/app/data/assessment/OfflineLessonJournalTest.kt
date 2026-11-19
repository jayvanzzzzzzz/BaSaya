package ph.basaya.app.data.assessment

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OfflineLessonJournalTest {
    private class Memory : SubmissionStorage {
        val values = mutableMapOf<String, String>()
        var failWrites = false
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String) {
            if (failWrites) throw IOException("Disk full")
            values[key] = value
        }
        override fun remove(key: String) { values.remove(key) }
    }
    private val seed = JsonParser.parseString("""{
      "content":{"counts":{"lecture":2,"quiz":2,"activity":2,"game":2},
        "quiz":{"title":"Quiz","questions":[{"choices":["a","b"]},{"choices":["c","d"]}]}},
      "activity":{"attemptId":"activity-attempt","contentVersion":"version","activity":{"pages":[
        {"words":["a","b"],"correctIndices":[1]},{"words":["c","d"],"correctIndices":[0]}]},
        "resume":{"done":0,"score":0,"retries":0,"lives":3}},
      "levels":[{"level":1,"words":["ISA","DALAWA"]},{"level":2,"words":["TATLO"]}],
      "begins":{"game":{"attemptId":"game-attempt"}},
      "progress":{"resume":{"activity":{"done":0,"score":0,"retries":0,"lives":3}}}
    }""").asJsonObject
    private fun body(op: String, part: String, extra: JsonObject = JsonObject()) = fields(
        "operation" to op, "part" to part, "classId" to "class", "progressSession" to "session", "lessonId" to "lesson"
    ).apply { extra.entrySet().forEach { add(it.key, it.value) } }
    private suspend fun OfflineLessonJournal.offline(op: String, part: String, extra: JsonObject = JsonObject()) =
        exchange(body(op, part, extra), false) { error("Offline flow contacted the server") }
    private suspend fun prepared(memory: Memory = Memory(), owner: () -> Boolean = { true }): OfflineLessonJournal =
        OfflineLessonJournal(memory, "student:class:session:lesson", owner).also { it.seed(seed) }
    private suspend fun OfflineLessonJournal.readLecture() {
        offline("resume", "lecture", fields("done" to 1)); offline("resume", "lecture", fields("done" to 2))
        offline("finish", "lecture")
    }

    @Test fun lectureSurvivesProcessRestartAndUnlocksQuiz() = runBlocking {
        val memory = Memory()
        prepared(memory).readLecture()
        val restarted = OfflineLessonJournal(memory, "student:class:session:lesson") { true }
        assertTrue(restarted.snapshot().flag("lectureFinished"))
        assertTrue(restarted.pending())
        assertTrue(restarted.offline("begin", "quiz").text("attemptId").startsWith("offline_"))
    }

    @Test fun lecturePagesCannotBeSkippedOrFinishedEarly() = runBlocking {
        val journal = prepared()
        expectFailure { journal.offline("resume", "lecture", fields("done" to 2)) }
        expectFailure { journal.offline("finish", "lecture") }
        assertFalse(journal.pending())
    }

    @Test fun quizRecordsAnswersWithoutInventingAnOfficialScore() = runBlocking {
        val journal = prepared(); journal.readLecture()
        val attempt = journal.offline("begin", "quiz").text("attemptId")
        for (index in 0..1) journal.offline("answer", "quiz", fields("attemptId" to attempt, "index" to index,
            "selected" to listOf(1), "eventId" to "answer-$index"))
        val complete = journal.offline("finish", "quiz", fields("attemptId" to attempt))
        assertTrue(complete.flag("finished")); assertTrue(complete.flag("pendingSync"))
        assertFalse(complete.has("score")); assertFalse(journal.snapshot().has("quizScore"))
        assertEquals(2, journal.offline("begin", "quiz").getAsJsonObject("resume").number("done"))
    }

    @Test fun quizRequiresLectureAndValidUniqueSelections() = runBlocking {
        val journal = prepared()
        expectFailure { journal.offline("answer", "quiz", fields("index" to 0, "selected" to listOf(1))) }
        journal.readLecture()
        expectFailure { journal.offline("answer", "quiz", fields("index" to 0, "selected" to listOf(7))) }
        expectFailure { journal.offline("answer", "quiz", fields("index" to 0, "selected" to emptyList<Int>())) }
        expectFailure { journal.offline("finish", "quiz") }
    }

    @Test fun activityWrongAttemptsAndLivesAreDurableAndResumeOnTheSamePage() = runBlocking {
        val journal = prepared()
        repeat(2) { index ->
            val result = journal.offline("answer", "activity", fields("index" to 0, "selected" to listOf(0), "eventId" to "wrong-$index"))
            assertFalse(result.flag("right")); assertEquals(2 - index, result.number("lives"))
        }
        val resume = journal.offline("prepare-activity", "activity").getAsJsonObject("resume")
        assertEquals(0, resume.number("done")); assertEquals(1, resume.number("lives")); assertEquals(2, resume.number("retries"))
        val right = journal.offline("answer", "activity", fields("index" to 0, "selected" to listOf(1), "eventId" to "right"))
        assertTrue(right.flag("right")); assertEquals(1, right.number("done")); assertEquals(1, right.number("score"))
        assertEquals(3, right.number("lives"))
    }

    @Test fun thirdWrongActivityAnswerMovesToNextPage() = runBlocking {
        val journal = prepared()
        repeat(3) { index -> journal.offline("answer", "activity", fields("index" to 0, "selected" to listOf(0), "eventId" to "wrong-$index")) }
        val resume = journal.offline("prepare-activity", "activity").getAsJsonObject("resume")
        assertEquals(1, resume.number("done")); assertEquals(3, resume.number("lives")); assertEquals(0, resume.number("score"))
    }

    @Test fun duplicateActivityDeliveryDoesNotConsumeAnotherLife() = runBlocking {
        val journal = prepared()
        val event = fields("index" to 0, "selected" to listOf(0), "eventId" to "same-event")
        assertEquals(journal.offline("answer", "activity", event), journal.offline("answer", "activity", event))
        assertEquals(2, journal.offline("prepare-activity", "activity").getAsJsonObject("resume").number("lives"))
    }

    @Test fun gameCanFinishAndAdvanceThroughEveryDownloadedLevelOffline() = runBlocking {
        val journal = prepared()
        journal.offline("answer", "game", fields("level" to 1, "word" to "isa"))
        assertEquals(1, journal.offline("begin", "game").getAsJsonObject("resume").number("done"))
        val first = journal.offline("finish", "game", fields("level" to 1, "words" to listOf("isa", "dalawa")))
        assertFalse(first.flag("finished")); assertEquals(2, journal.offline("begin", "game").number("level"))
        val last = journal.offline("finish", "game", fields("level" to 2, "words" to listOf("tatlo")))
        assertTrue(last.flag("finished")); assertTrue(journal.snapshot().flag("gameFinished"))
    }

    @Test fun failedStorageWriteDoesNotAdvanceTheSavedState() = runBlocking {
        val memory = Memory(); val journal = prepared(memory)
        memory.failWrites = true
        try { journal.offline("resume", "lecture", fields("done" to 1)); fail("Expected storage error") } catch (_: IOException) { }
        memory.failWrites = false
        assertFalse(journal.pending())
        assertEquals(0, journal.snapshot().getAsJsonObject("resume").getAsJsonObject("lecture")?.number("done") ?: 0)
    }

    @Test fun replayKeepsOrderAndRetainsOnlyUnacceptedCommandsAfterConnectionLoss() = runBlocking {
        val journal = prepared(); journal.readLecture()
        val sent = mutableListOf<String>()
        try {
            journal.replay { command ->
                sent.add(command.text("operation") + command.number("done"))
                if (sent.size == 2) throw IOException("Disconnected")
                fields("finished" to false)
            }
            fail("Expected interruption")
        } catch (_: IOException) { }
        journal.replay { command -> sent.add(command.text("operation") + command.number("done")); fields("finished" to true) }
        assertEquals(listOf("resume1", "resume2", "resume2", "finish0"), sent)
        assertFalse(journal.pending())
    }

    @Test fun quizServerAttemptMappingSurvivesDisconnectAndReusesTheSameEvent() = runBlocking {
        val memory = Memory(); var journal = prepared(memory); journal.readLecture()
        val localId = journal.offline("begin", "quiz").text("attemptId")
        journal.offline("answer", "quiz", fields("attemptId" to localId, "index" to 0, "selected" to listOf(1), "eventId" to "stable"))
        var begins = 0; var disconnected = false
        val received = mutableListOf<String>()
        val send: suspend (JsonObject) -> JsonObject = { command ->
            when (command.text("operation")) {
                "content" -> seed.getAsJsonObject("content").deepCopy()
                "begin" -> { begins++; fields("attemptId" to "server-attempt") }
                "answer" -> {
                    received.add(command.text("attemptId") + ":" + command.text("eventId"))
                    if (!disconnected) { disconnected = true; throw IOException("Lost reply") }
                    fields("done" to 1, "score" to 1, "total" to 2)
                }
                else -> fields("finished" to true)
            }
        }
        try { journal.replay(send) } catch (_: IOException) { }
        journal = OfflineLessonJournal(memory, "student:class:session:lesson") { true }
        journal.replay(send)
        assertEquals(1, begins)
        assertEquals(listOf("server-attempt:stable", "server-attempt:stable"), received)
        assertFalse(journal.pending())
    }

    @Test fun rejectedWorkIsRetainedAndIsNotAutomaticallyRetried() = runBlocking {
        val journal = prepared(); journal.readLecture()
        expectFailure { journal.replay { throw AssessmentFailure(403, "Deadline passed") } }
        assertTrue(journal.pending()); assertEquals("Deadline passed", journal.blocked())
        var called = false
        expectFailure { journal.replay { called = true; JsonObject() } }
        assertFalse(called)
    }

    @Test fun anotherAccountCannotReadOrSubmitSavedWork() = runBlocking {
        var owner = true; val journal = prepared(owner = { owner }); journal.readLecture(); owner = false
        expectFailure { journal.offline("begin", "quiz") }
        expectFailure { journal.replay { error("Must not send another user's work") } }
    }

    @Test fun redownloadDoesNotErasePendingAnswers() = runBlocking {
        val journal = prepared(); journal.readLecture(); journal.seed(seed)
        assertTrue(journal.pending()); assertTrue(journal.snapshot().flag("lectureFinished"))
    }

    @Test fun confirmedQuizScoreRemainsAvailableLocallyAfterTheQueueIsSent() = runBlocking {
        val journal = prepared(); journal.readLecture()
        val attempt = journal.offline("begin", "quiz").text("attemptId")
        repeat(2) { journal.offline("answer", "quiz", fields("attemptId" to attempt, "index" to it, "selected" to listOf(1), "eventId" to "q$it")) }
        journal.offline("finish", "quiz", fields("attemptId" to attempt))
        journal.replay { command -> when(command.text("operation")) {
            "content" -> seed.getAsJsonObject("content")
            "begin" -> fields("attemptId" to "server-attempt")
            else -> fields("finished" to true, "score" to 1, "total" to 2)
        } }
        assertEquals(1, journal.snapshot().number("quizScore")); assertEquals(2, journal.snapshot().number("quizTotal"))
        assertFalse(journal.pending())
    }

    @Test fun changedPayloadCannotReuseAnEarlierSubmissionId() = runBlocking {
        val journal = prepared()
        journal.offline("answer", "activity", fields("index" to 0, "selected" to listOf(0), "eventId" to "fixed"))
        expectFailure { journal.offline("answer", "activity", fields("index" to 0, "selected" to listOf(1), "eventId" to "fixed")) }
        assertEquals(2, journal.offline("prepare-activity", "activity").getAsJsonObject("resume").number("lives"))
    }

    @Test fun practiceReplaysOnlyAnswersAndCanBeRepeatedWithoutOfficialCompletions() = runBlocking {
        val journal = prepared(); journal.readLecture()
        suspend fun practice(op: String, extra: JsonObject = JsonObject()) = journal.exchange(
            body(op, "quiz", extra).apply { addProperty("practice", true); addProperty("entryId", "lesson__practice") }, false) { error("Offline") }
        val attempt = practice("begin").text("attemptId")
        repeat(2) { practice("answer", fields("attemptId" to attempt, "index" to it, "selected" to listOf(1), "eventId" to "practice$it")) }
        practice("finish", fields("attemptId" to attempt))
        val again = practice("begin")
        assertFalse(again.flag("finished")); assertNotEquals(attempt, again.text("attemptId"))
        var practiceAnswers = 0
        journal.replay { command ->
            if (command.flag("practice")) { assertEquals("answer", command.text("operation")); practiceAnswers++ }
            fields("finished" to true)
        }
        assertEquals(2, practiceAnswers)
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try { action(); fail("Expected assessment rejection") } catch (_: AssessmentFailure) { }
    }
}
