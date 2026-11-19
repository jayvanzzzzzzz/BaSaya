package ph.basaya.app.data.assessment

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Optional contract test against the real assessment handler backed by a local Firestore emulator. */
class OfflineAssessmentReplayTest {
    @Test fun allFourPartsCompletedOfflineAreVerifiedByTheServerAfterReconnect() = runBlocking {
        val base = System.getenv("BASAYA_OFFLINE_TEST_SERVER").orEmpty()
        assumeTrue("Start the isolated assessment test bridge to run this contract test", base.isNotBlank())
        fun http(path: String, body: JsonObject? = null): JsonObject {
            val connection = URL(base + path).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000; connection.readTimeout = 60_000
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            try {
                val status = connection.responseCode
                val result = JsonParser.parseString((if (status == 200) connection.inputStream else connection.errorStream)
                    .bufferedReader().use { it.readText() }).asJsonObject
                if (status != 200) throw AssessmentFailure(status, result.text("error"))
                return result
            } finally { connection.disconnect() }
        }
        val fixture = http("/seed")
        val memory = mutableMapOf<String, String>()
        val storage = object : SubmissionStorage {
            override fun read(key: String) = memory[key]
            override fun write(key: String, value: String) { memory[key] = value }
            override fun remove(key: String) { memory.remove(key) }
        }
        var journal = OfflineLessonJournal(storage, "fixture") { true }
        journal.seed(fixture)
        suspend fun request(op: String, part: String, extra: JsonObject = JsonObject()) =
            journal.exchange(fields("operation" to op, "part" to part, "classId" to "offline-class",
                "progressSession" to "offline-session", "lessonId" to "offline-lesson").apply {
                extra.entrySet().forEach { add(it.key, it.value) }
            }, false) { error("Offline work must not contact the server") }

        request("resume", "lecture", fields("done" to 1)); request("resume", "lecture", fields("done" to 2)); request("finish", "lecture")
        val activity = request("prepare-activity", "activity")
        for ((event, index, selected) in listOf(Triple("wrong", 0, listOf(0)), Triple("right", 0, listOf(1)), Triple("second", 1, listOf(0)))) {
            request("answer", "activity", fields("index" to index, "selected" to selected, "eventId" to event,
                "attemptId" to activity.text("attemptId"), "contentVersion" to activity.text("contentVersion")))
        }
        request("finish", "activity", fields("attemptId" to activity.text("attemptId"), "contentVersion" to activity.text("contentVersion")))
        val game = request("begin", "game")
        request("answer", "game", fields("level" to 1, "word" to "ISA", "eventId" to "word-one", "attemptId" to game.text("attemptId")))
        request("finish", "game", fields("level" to 1, "words" to listOf("ISA", "DALAWA"), "attemptId" to game.text("attemptId")))
        request("finish", "game", fields("level" to 2, "words" to listOf("TATLO"), "attemptId" to game.text("attemptId")))
        val quiz = request("begin", "quiz")
        request("answer", "quiz", fields("index" to 0, "selected" to listOf(1), "eventId" to "quiz-one", "attemptId" to quiz.text("attemptId")))
        request("answer", "quiz", fields("index" to 1, "selected" to listOf(0, 2), "eventId" to "quiz-two", "attemptId" to quiz.text("attemptId")))
        val complete = request("finish", "quiz", fields("attemptId" to quiz.text("attemptId")))
        assertTrue(complete.flag("pendingSync")); assertFalse(complete.has("score"))
        assertFalse(http("/progress").flag("lectureFinished"))

        // Recreate the app's journal, then reconnect. The handler—not the client—calculates all official scores.
        journal = OfflineLessonJournal(storage, "fixture") { true }
        journal.replay { http("/assessment", it) }
        assertFalse(journal.pending())
        val official = http("/progress")
        for (part in listOf("lecture", "activity", "game", "quiz")) assertTrue("$part did not sync", official.flag("${part}Finished"))
        assertEquals(2, official.number("activityScore")); assertEquals(2, official.number("gameScore")); assertEquals(2, official.number("quizScore"))
        assertEquals(1, official.getAsJsonObject("skills").getAsJsonObject("pagkilala").number("retries"))
    }
}
