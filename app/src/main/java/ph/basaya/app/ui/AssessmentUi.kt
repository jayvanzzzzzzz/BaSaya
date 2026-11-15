package ph.basaya.app.ui

import ph.basaya.app.ui.feedback.StudioDialogBuilder
import ph.basaya.app.ui.feedback.FeedbackTone
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ph.basaya.app.data.assessment.AssessmentFailure

/** Keep the activity alive and locked until a command is accepted or explicitly left pending. */
object AssessmentUi {
    fun run(activity: AppCompatActivity, showPending: Boolean = true, action: suspend () -> Unit): kotlinx.coroutines.Job? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val pending = if (showPending) StudioDialogBuilder(activity, FeedbackTone.WARNING)
            .setTitle("Kinukumpirma ang progreso")
            .setMessage("Hinihintay ang pagtanggap ng server. Hindi pa ito kumpirmadong tapos.")
            .setCancelable(false)
            .setNegativeButton("Bumalik sa aralin") { _, _ -> activity.finish() }.show() else null
        return activity.lifecycleScope.launch {
            try {
                var attempt = 0
                while (true) {
                    try { action(); return@launch }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        val retryable = (e as? AssessmentFailure)?.retryable ?: true
                        if (retryable && attempt++ < 2) { delay(1000L * attempt); continue }
                        if (activity.isFinishing || activity.isDestroyed) return@launch
                        val message = if (retryable)
                            "Hindi pa nakumpirma ang pagpapasa. Kung may sagot nang naipadala, nakatala ito sa telepono at susubukan muli kapag binuksan ang aralin. Kumonekta at subukan muli."
                        else if ((e as? AssessmentFailure)?.status == 403)
                            "Hindi tinanggap ng server ang pagpapasa. Maaaring lumipas na ang deadline o nagbago ang pagkaka-assign. Hindi ito mamarkahang tapos. Bumalik sa aralin upang makita ang naitalang progreso; kausapin ang guro kung kailangan itong buksan muli."
                        else if ((e as? AssessmentFailure)?.status == 401)
                            "Hindi makumpirma ang account. Mag-log in muli sa orihinal na account; hindi pa nakumpirma ang pagpapasa."
                        else "Nagbago ang aralin o pagtatangka. Hindi ito mamarkahang tapos. Buksan muli ang aralin para makita ang naitalang progreso."
                        pending?.dismiss()
                        val dialog = StudioDialogBuilder(activity, FeedbackTone.WARNING)
                            .setTitle(if (retryable) "Hinihintay ang pagpapasa" else "Hindi naitala ang resulta")
                            .setMessage(message)
                            .setCancelable(false)
                            .setNegativeButton("Bumalik sa aralin") { _, _ -> activity.finish() }
                        if (retryable) dialog.setPositiveButton("Subukan muli") { _, _ -> AssessmentUi.run(activity, showPending = showPending, action = action) }
                        dialog.show()
                        return@launch
                    }
                }
            } finally { pending?.dismiss() }
        }
    }
}
