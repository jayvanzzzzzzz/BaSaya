package ph.basaya.app.ui.feedback

import android.animation.AnimatorInflater
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ph.basaya.app.R

enum class FeedbackTone(val color: Int, val icon: Int) {
    INFO(R.color.fb_blue, R.drawable.ic_info),
    SUCCESS(R.color.fb_success, R.drawable.ic_check),
    WARNING(R.color.fb_warning, R.drawable.ic_feedback_warning),
    DANGER(R.color.fb_danger, R.drawable.ic_feedback_warning)
}

/** Shared window and touch treatment; business callbacks stay with each screen. */
internal object StudioFeedback {
    fun decorate(view: View) {
        if (view.isClickable && view !is EditText && (view is TextView || view is ImageView)) {
            view.minimumHeight = maxOf(view.minimumHeight, (48 * view.resources.displayMetrics.density).toInt())
            view.stateListAnimator = AnimatorInflater.loadStateListAnimator(view.context, R.animator.feedback_press)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) decorate(view.getChildAt(i))
    }
}

/** XML confirmations retain their IDs, cancellation policy and click listeners. */
class StudioDialog(context: Context) : Dialog(context, R.style.Theme_BASAya_StudioDialog) {
    override fun onStart() {
        super.onStart()
        val decor = window?.decorView ?: return
        StudioFeedback.decorate(decor)
        decor.doOnLayout {
            if (!isShowing) return@doOnLayout
            val metrics = context.resources.displayMetrics
            val width = minOf(decor.width, (420 * metrics.density).toInt(), metrics.widthPixels - (32 * metrics.density).toInt())
            val heightLimit = (metrics.heightPixels * .85f).toInt()
            window?.setLayout(width, if (decor.height > heightLimit) heightLimit else ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}

/** Native AlertDialog behavior, including validation that keeps its positive button open. */
class StudioDialogBuilder(context: Context, private val tone: FeedbackTone = FeedbackTone.INFO) :
    MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_BASAya_StudioAlert) {
    private val heading = LayoutInflater.from(this.context).inflate(R.layout.feedback_alert_heading, null)

    init {
        setBackground(ContextCompat.getDrawable(this.context, R.drawable.fb_panel))
        super.setCustomTitle(heading)
        heading.findViewById<ImageView>(R.id.feedbackHeadingIcon).apply {
            setImageResource(tone.icon)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.fb_on_color))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tone.color))
        }
    }

    override fun setTitle(title: CharSequence?): StudioDialogBuilder {
        super.setTitle(title)
        heading.findViewById<TextView>(R.id.feedbackHeadingTitle).text = title
        return this
    }

    override fun setTitle(titleId: Int): StudioDialogBuilder = setTitle(context.getText(titleId))

    override fun create(): AlertDialog {
        val dialog = super.create()
        dialog.window?.setWindowAnimations(R.style.Animation_BASAya_Feedback)
        // Avoid replacing the caller's OnShowListener (password-reset validation uses it).
        dialog.window?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.post {
                    StudioFeedback.decorate(v)
                    for (id in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
                        dialog.getButton(id)?.apply {
                            typeface = ResourcesCompat.getFont(context, R.font.be_vietnam_bold)
                            isSingleLine = false
                            maxLines = Int.MAX_VALUE
                            ellipsize = null
                        }
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { button ->
                        button.setTextColor(ContextCompat.getColorStateList(context, R.color.fb_action_text))
                        button.backgroundTintList = ContextCompat.getColorStateList(context,
                            if (tone == FeedbackTone.DANGER) R.color.fb_danger_tint else R.color.fb_action_tint)
                        (button as? MaterialButton)?.cornerRadius = (16 * context.resources.displayMetrics.density).toInt()
                    }
                }
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        return dialog
    }
}
