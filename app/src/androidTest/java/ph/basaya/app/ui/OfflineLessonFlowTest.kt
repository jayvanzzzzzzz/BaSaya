package ph.basaya.app.ui

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import ph.basaya.app.R
import ph.basaya.app.data.assessment.*
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.*
import ph.basaya.app.data.model.LecturePage
import ph.basaya.app.data.audio.LectureAudioStore
import ph.basaya.app.data.audio.LectureAudioCache
import ph.basaya.app.data.progress.ProgressSession
import ph.basaya.app.ui.activity.PreparedPractice
import ph.basaya.app.ui.game.PreparedCrossword
import ph.basaya.app.ui.game.GameWordSaves
import ph.basaya.app.ui.quiz.PreparedQuiz
import ph.basaya.app.ui.quiz.QuizAlreadyFinished
import ph.basaya.app.ui.quiz.QuizCompleteScreen
import ph.basaya.app.utils.Network
import java.io.File
import java.util.UUID

/** Uses only random local lesson IDs. No real student answers or server records are changed. */
@RunWith(AndroidJUnit4::class)
class OfflineLessonFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val db = AppDatabase.getDatabase(context)

    @Test fun undownloadedContentShowsDismissibleInternetMessage() = offline {
        val id = "offline_qa_${UUID.randomUUID()}"
        ActivityScenario.launch<DashboardActivity>(Intent(context, DashboardActivity::class.java)
            .putExtra("LESSON_ID", id).putExtra(DashboardActivity.EXTRA_PREPARE_GAME, true)).use {
            onView(withText(R.string.lesson_internet_required_title)).check(matches(isDisplayed()))
            onView(withText(R.string.lesson_internet_required_message)).check(matches(isDisplayed()))
            onView(withText("OK")).perform(click())
            SystemClock.sleep(500)
            onView(withId(R.id.gameCard)).check(matches(isDisplayed()))
        }
    }

    @Test fun downloadedScreensAndPreparationsWorkOfflineAndKeepDurableProgress() = offline {
        val user = FirebaseAuth.getInstance().currentUser
        assumeNotNull("A signed-in test phone is needed", user)
        val uid = user!!.uid
        val id = "offline_qa_${UUID.randomUUID()}"
        val stamp = ProgressSession.stamp(uid, emptyMap())
        val journal = OfflineLessons.journal(context, id, uid)
        val prefs = context.getSharedPreferences("offline_lesson_work_v1", 0)
        try {
            runBlocking {
                db.lessonDao().insertAll(listOf(LessonEntity(id = id, userId = uid,
                    classId = stamp["classId"].toString(), title = "Offline QA", description = "Local fixture",
                    difficulty = "", order = 999, unlocked = true, isDownloaded = true)))
                val pages = listOf(LecturePage(pageNumber = 1, sentences = listOf("Offline page one"), audioUrls = listOf("https://offline.invalid/one.mp3")),
                    LecturePage(pageNumber = 2, sentences = listOf("Offline page two"), audioUrls = listOf("https://offline.invalid/two.mp3")))
                for (page in pages) {
                    val audio = LectureAudioStore.localFile(context, id, page.pageNumber, 0)
                    audio.parentFile!!.mkdirs()
                    context.resources.openRawResource(R.raw.lesson1_page1_sentence1).use { input -> audio.outputStream().use { input.copyTo(it) } }
                    assertTrue(LectureAudioCache.preparePage(context, id, page).await())
                }
                db.lectureDao().insert(LectureEntity(id, "Offline QA lecture", Gson().toJson(pages)))
                db.crosswordLevelDao().insertAll(listOf(
                    CrosswordLevelEntity("${id}_1", id, 1, "ISA,DALAWA", 1),
                    CrosswordLevelEntity("${id}_2", id, 2, "TATLO", 2)))
                journal.seed(fields("lessonId" to id, "uid" to uid,
                    "content" to fields("counts" to fields("lecture" to 2, "game" to 2, "activity" to 2, "quiz" to 2),
                        "quiz" to fields("title" to "Offline QA quiz", "questions" to listOf(
                            fields("question" to "One?", "choices" to listOf("A", "B"), "multiple" to false),
                            fields("question" to "Two?", "choices" to listOf("C", "D"), "multiple" to false)))),
                    "activity" to fields("attemptId" to "activity-test", "contentVersion" to "version",
                        "activity" to fields("title" to "Activity", "pages" to listOf(
                            fields("words" to listOf("A", "B"), "correctIndices" to listOf(1)),
                            fields("words" to listOf("C", "D"), "correctIndices" to listOf(0))))),
                    "begins" to fields("game" to fields("attemptId" to "game-test")),
                    "levels" to listOf(fields("level" to 1, "words" to listOf("ISA", "DALAWA")),
                        fields("level" to 2, "words" to listOf("TATLO"))), "progress" to fields("resume" to fields())))
            }
            ActivityScenario.launch<LectureActivity>(Intent(context, LectureActivity::class.java).putExtra("LESSON_ID", id)).use { scenario ->
                await { var loaded = false; scenario.onActivity { loaded = it.findViewById<TextView>(R.id.tvLectureTitle).text == "Offline QA lecture" }; loaded }
                onView(withId(R.id.btnNextPage)).perform(click())
                await { journal.snapshot().getAsJsonObject("resume").getAsJsonObject("lecture")?.number("done") == 1 }
                onView(withId(R.id.btnNextPage)).perform(click())
                await { journal.snapshot().flag("lectureFinished") }
            }
            runBlocking {
                val practice = PreparedPractice.prepare(context, id)
                assertTrue(practice.content.pages[0].answerKeyLoaded)
                practice.assessment!!.request("answer", "activity", fields("index" to 0, "selected" to listOf(1),
                    "attemptId" to practice.attemptId, "contentVersion" to practice.contentVersion, "eventId" to "qa-activity"))
                assertEquals(1, PreparedPractice.prepare(context, id).pageIndex)

                val game = PreparedCrossword.prepare(context, id)
                GameWordSaves.send(context, game.progressKey, game.levels, game.assessment,
                    fields("operation" to "finish", "level" to 1, "words" to listOf("ISA", "DALAWA"), "attemptId" to game.attemptId), game::isCurrent)
                assertEquals(2, PreparedCrossword.prepare(context, id).level.level)

                val quiz = PreparedQuiz.prepare(context, id)
                assertEquals(2, quiz.quiz.questions.size)
                repeat(2) { index -> quiz.assessment!!.request("answer", "quiz",
                    fields("index" to index, "selected" to listOf(1), "attemptId" to quiz.attemptId)) }
                val finished = quiz.assessment!!.finish("quiz", fields("attemptId" to quiz.attemptId))
                assertTrue(finished.flag("pendingSync")); assertTrue(journal.pending())
                try { PreparedQuiz.prepare(context, id); fail("Completed local quiz should be protected from duplicate attempts") }
                catch (_: QuizAlreadyFinished) { }
            }
            ActivityScenario.launch<QuizCompleteScreen>(Intent(context, QuizCompleteScreen::class.java)
                .putExtra("LESSON_ID", id).putExtra("TOTAL", 2).putExtra("PENDING_SYNC", true)).use {
                onView(withText(R.string.lesson_offline_quiz_pending)).check(matches(isDisplayed()))
                onView(withId(R.id.tvScore)).check(matches(withText("—")))
            }
        } finally {
            runBlocking {
                db.lectureDao().deleteByLessonId(id); db.crosswordLevelDao().deleteByLessonId(id)
                db.openHelper.writableDatabase.execSQL("DELETE FROM lessons WHERE id = ?", arrayOf(id))
                db.openHelper.writableDatabase.execSQL("DELETE FROM game_progress WHERE lessonId = ?", arrayOf(ProgressSession.gameKey(id, uid)))
            }
            prefs.edit().apply { prefs.all.keys.filter { it.endsWith(":$id") }.forEach(::remove) }.commit()
            File(context.filesDir, "lecture_audio/$id").deleteRecursively()
        }
    }

    private fun offline(action: () -> Unit) {
        val wifi = shell("settings get global wifi_on").trim() == "1"
        val data = shell("settings get global mobile_data").trim() == "1"
        try {
            shell("svc wifi disable"); shell("svc data disable")
            await { !Network.isOnline(context) }
            action()
        } finally {
            if (wifi) shell("svc wifi enable")
            if (data) shell("svc data enable")
        }
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Offline flow timed out")
            SystemClock.sleep(100)
        }
    }
}
