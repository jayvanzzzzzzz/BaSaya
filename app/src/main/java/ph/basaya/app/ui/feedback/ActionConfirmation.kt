package ph.basaya.app.ui.feedback

import android.app.Activity
import android.content.Context
import java.util.WeakHashMap

/** Confirmation only; callers keep validation, network writes and loading states. */
object ActionConfirmation {
    private val showing = WeakHashMap<Context, Boolean>()
    fun show(context: Context, title: String, message: String, action: String,
             danger: Boolean = false, onCancel: () -> Unit = {}, onConfirm: () -> Unit) {
        if ((context as? Activity)?.let { it.isFinishing || it.isDestroyed } == true) return
        if (showing[context] == true) return
        showing[context] = true
        var accepted = false
        val dialog = StudioDialogBuilder(context, if (danger) FeedbackTone.DANGER else FeedbackTone.INFO)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Bumalik", null)
            .setPositiveButton(action) { _, _ ->
                accepted = true
                if ((context as? Activity)?.let { it.isFinishing || it.isDestroyed } != true) onConfirm()
            }.create()
        dialog.setOnDismissListener {
            showing.remove(context)
            if (!accepted) onCancel()
        }
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }
}
