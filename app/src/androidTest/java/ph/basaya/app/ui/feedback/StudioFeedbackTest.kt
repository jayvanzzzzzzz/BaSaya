package ph.basaya.app.ui.feedback

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ph.basaya.app.R
import ph.basaya.app.ui.LogInActivity
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Integration checks for shared presentation without changing accounts or server data. */
@RunWith(AndroidJUnit4::class)
class StudioFeedbackTest {
    @Test fun validationCanKeepDialogOpenAndConfirmationRunsOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val confirmed = AtomicInteger()
        val attempts = AtomicInteger()
        lateinit var dialog: AlertDialog
        ActivityScenario.launch(LogInActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.layoutInflater.inflate(R.layout.dialog_forgot_password, null)
                dialog = StudioDialogBuilder(activity)
                    .setTitle(R.string.forgot_title).setView(content)
                    .setPositiveButton(R.string.forgot_send, null).create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { attempts.incrementAndGet() }
                }
                dialog.show()
            }
            // OnShow is dispatched through the main looper before a real user can tap.
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertEquals(1, attempts.get())
                assertTrue("Validation must retain the dialog", dialog.isShowing)
                snapshot(dialog.window!!.decorView, "reset-native", dialog.window!!.decorView.width, 2000)
                // An open dialog takes focus from the activity; its feedback must still be styled.
                @Suppress("DEPRECATION")
                val toast = StudioToast.makeText(activity, R.string.pf_saved, StudioToast.LENGTH_SHORT).view
                assertNotNull("Foreground feedback is available while a dialog is open", toast)
                assertEquals(activity.getString(R.string.pf_saved), toast!!.findViewById<TextView>(R.id.feedbackToastMessage).text.toString())
                snapshot(toast, "toast-success", activity.resources.displayMetrics.widthPixels - 84, 800)
                dialog.dismiss()
                StudioDialogBuilder(activity, FeedbackTone.DANGER)
                    .setTitle(R.string.ch_delete_title).setMessage(R.string.ch_delete_body)
                    .setNegativeButton(R.string.ch_delete_no, null)
                    .setPositiveButton(R.string.ch_delete_yes) { _, _ -> confirmed.incrementAndGet() }
                    .show().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            assertEquals("One tap must dispatch exactly one confirmation", 1, confirmed.get())
        }
    }

    @Test fun confirmationLayoutsRenderAt320dpInBothThemesAndLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (night in listOf(false, true)) for (fontScale in listOf(1f, 1.6f)) {
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                    densityDpi = 160
                    this.fontScale = fontScale
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(config), R.style.Theme_BASAya_StudioDialog)
                for ((name, layout) in listOf("logout" to R.layout.dialog_logout,
                    "leave" to R.layout.dialog_leave_class, "status" to R.layout.dialog_lesson_status,
                    "offline" to R.layout.dialog_offline_lesson, "reading" to R.layout.dialog_lecture_exit,
                    "reset-body" to R.layout.dialog_forgot_password)) {
                    val view = LayoutInflater.from(context).inflate(layout, null)
                    view.findViewById<ImageView>(R.id.ivDialogIcon)?.setImageResource(R.drawable.ic_lock_small)
                    view.findViewById<TextView>(R.id.tvDialogTitle)?.setText(R.string.schedule_not_open_title)
                    view.findViewById<TextView>(R.id.tvDialogMessage)?.setText(R.string.lecture_audio_offline)
                    view.findViewById<TextView>(R.id.btnDialogPrimary)?.text = "OK"
                    view.findViewById<TextView>(R.id.tvOfflineMessage)?.setText(R.string.lecture_audio_offline)
                    view.findViewById<TextView>(R.id.tvLeaveTitle)?.setText(R.string.pf_leave_dialog_title)
                    view.findViewById<TextView>(R.id.tvLeaveMessage)?.text = context.getString(R.string.pf_leave_dialog_message, "Filipino 7")
                    view.findViewById<TextView>(R.id.btnLeaveConfirm)?.setText(R.string.pf_leave_dialog_confirm)
                    view.findViewById<TextView>(R.id.btnLeaveCancel)?.setText(R.string.pf_leave_dialog_stay)
                    view.findViewById<TextView>(R.id.lexPage)?.text = context.getString(R.string.lex_page, 3, 8)
                    view.findViewById<TextView>(R.id.lexMessage)?.text = context.getString(R.string.lex_message_saved, 3)
                    snapshot(view, "$name-${if (night) "dark" else "light"}-$fontScale", 320, 620)
                    assertEquals(320, view.measuredWidth)
                    assertTrue("$name must fit a compact viewport", view.measuredHeight in 1..620)
                }
            }
        }
    }

    private fun snapshot(view: View, name: String, width: Int, maxHeight: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val image = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "feedback-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
