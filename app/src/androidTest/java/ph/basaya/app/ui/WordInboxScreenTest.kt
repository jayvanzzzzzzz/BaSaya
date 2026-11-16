package ph.basaya.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ph.basaya.app.R
import ph.basaya.app.data.notifications.LessonInbox
import ph.basaya.app.ui.dictionary.DictionaryActivity
import ph.basaya.app.ui.notifications.NotificationsActivity
import java.io.File

/** Exercises the bundled dictionary and inbox presentation without changing account data. */
@RunWith(AndroidJUnit4::class)
class WordInboxScreenTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun shot(activity: Activity, name: String) {
        val bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
        activity.window.decorView.draw(android.graphics.Canvas(bitmap))
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it) } ?: activity.getExternalFilesDir("word-inbox-qa")!!
        output.mkdirs()
        File(output, "word-inbox-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun waitForWords(scenario: ActivityScenario<DictionaryActivity>) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            scenario.onActivity {
                ready = it.findViewById<RecyclerView>(R.id.recyclerWords).visibility == View.VISIBLE &&
                    it.findViewById<RecyclerView>(R.id.recyclerWords).findViewById<TextView>(R.id.tvWord) != null
            }
            if (ready) return
            Thread.sleep(100)
        }
        fail("The bundled dictionary did not render")
    }

    @Test fun dictionarySearchDetailsClearAndBothThemes() {
        val original = AppCompatDelegate.getDefaultNightMode()
        try {
            for ((name, mode) in listOf("light" to AppCompatDelegate.MODE_NIGHT_NO, "dark" to AppCompatDelegate.MODE_NIGHT_YES)) {
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
                ActivityScenario.launch(DictionaryActivity::class.java).use { scenario ->
                    waitForWords(scenario)
                    Thread.sleep(300)
                    scenario.onActivity { shot(it, "dictionary-$name") }
                    scenario.onActivity { it.findViewById<EditText>(R.id.etSearch).setText("bahay") }
                    Thread.sleep(700)
                    waitForWords(scenario)
                    scenario.onActivity { it.findViewById<RecyclerView>(R.id.recyclerWords).findViewById<View>(R.id.wordCard).performClick() }
                    Thread.sleep(300)
                    scenario.onActivity {
                        assertEquals(View.VISIBLE, it.findViewById<View>(R.id.detailScroll).visibility)
                        shot(it, "definition-$name")
                        it.findViewById<View>(R.id.btnBack).performClick()
                        assertEquals(View.VISIBLE, it.findViewById<View>(R.id.recyclerWords).visibility)
                        it.findViewById<View>(R.id.btnClearSearch).performClick()
                        assertTrue(it.findViewById<EditText>(R.id.etSearch).text.isEmpty())
                    }
                }
            }
        } finally { instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(original) } }
    }

    @Test fun inboxFiltersRespectReadBoundaryAndPersistAcrossRecreation() {
        val original = AppCompatDelegate.getDefaultNightMode()
        try {
            for ((name, mode) in listOf("light" to AppCompatDelegate.MODE_NIGHT_NO, "dark" to AppCompatDelegate.MODE_NIGHT_YES)) {
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
                val boundary = System.currentTimeMillis() - 3_600_000L
                val intent = Intent(instrumentation.targetContext, NotificationsActivity::class.java).putExtra("readBefore", boundary)
                ActivityScenario.launch<NotificationsActivity>(intent).use { scenario ->
                    Thread.sleep(300)
                    scenario.onActivity {
                        shot(it, "inbox-empty-$name")
                        // In-memory fixtures only: no writes to Room, Firebase, or read state.
                        val fixture = listOf(
                            LessonInbox.Item("new", LessonInbox.Kind.NEW_LESSON, "Mga Pangngalan", "May bagong aralin para sa iyo.", boundary + 3_000_000L, listOf("preview")),
                            LessonInbox.Item("hour", LessonInbox.Kind.DEADLINE_HOUR, "1 oras na lang!", "Tapusin ang aralin bago ang deadline.", boundary + 2_400_000L, listOf("preview")),
                            LessonInbox.Item("old", LessonInbox.Kind.CLOSED, "Sarado na ang aralin", "Lumipas na ang deadline ng aralin.", boundary - 86_400_000L, listOf("preview"))
                        )
                        NotificationsActivity::class.java.getDeclaredField("items").apply { isAccessible = true }.set(it, fixture)
                        NotificationsActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(it)
                        assertEquals(5, it.findViewById<RecyclerView>(R.id.recyclerNotifications).adapter!!.itemCount)
                    }
                    Thread.sleep(400)
                    scenario.onActivity {
                        shot(it, "inbox-$name")
                        it.findViewById<View>(R.id.filterNew).performClick()
                        assertEquals(3, it.findViewById<RecyclerView>(R.id.recyclerNotifications).adapter!!.itemCount)
                        assertTrue(it.findViewById<View>(R.id.filterNew).isSelected)
                    }
                    scenario.recreate()
                    scenario.onActivity { assertTrue(it.findViewById<View>(R.id.filterNew).isSelected) }
                }
            }
        } finally { instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(original) } }
    }
}
