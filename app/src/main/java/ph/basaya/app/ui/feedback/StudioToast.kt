package ph.basaya.app.ui.feedback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import ph.basaya.app.R

/** Styled foreground toasts with the platform's queue, duration and accessibility events. */
object StudioToast {
    const val LENGTH_SHORT = Toast.LENGTH_SHORT
    const val LENGTH_LONG = Toast.LENGTH_LONG

    private val successMessages = intArrayOf(R.string.pf_saved, R.string.pf_password_changed,
        R.string.pf_leave_sent, R.string.pf_leave_cancelled, R.string.jc_request_approved,
        R.string.toast_download_success, R.string.toast_remove_download_success)

    fun makeText(context: Context?, textId: Int, duration: Int): Toast {
        val owner = requireNotNull(context)
        val name = owner.resources.getResourceEntryName(textId)
        val tone = when {
            textId in successMessages -> FeedbackTone.SUCCESS
            listOf("failed", "error", "not_found", "missing", "unavailable").any { name.contains(it) } -> FeedbackTone.DANGER
            listOf("offline", "locked", "too_fast", "need_internet", "connection", "need_login").any { name.contains(it) } -> FeedbackTone.WARNING
            else -> FeedbackTone.INFO
        }
        return build(owner, owner.getText(textId), duration, tone)
    }

    fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast {
        val owner = requireNotNull(context)
        val tone = when {
            successMessages.any { owner.getText(it) == text } || text == "Login Success" -> FeedbackTone.SUCCESS
            text == owner.getText(R.string.toast_download_failed) || text == owner.getText(R.string.toast_remove_download_failed) -> FeedbackTone.DANGER
            text == owner.getText(R.string.schedule_download_locked) -> FeedbackTone.WARNING
            else -> FeedbackTone.INFO
        }
        return build(owner, text ?: "", duration, tone)
    }

    @Suppress("DEPRECATION") // Custom toast views remain supported for foreground apps.
    private fun build(context: Context, text: CharSequence, duration: Int, tone: FeedbackTone): Toast {
        val activity = activity(context)
        // Android restricts custom background toasts; retain a readable system message there.
        if (activity == null || activity.isFinishing || activity.isDestroyed ||
            (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == false) {
            return Toast.makeText(context.applicationContext, text, duration)
        }
        val root = LayoutInflater.from(context).inflate(R.layout.feedback_toast, null)
        val density = context.resources.displayMetrics.density
        val width = minOf(context.resources.displayMetrics.widthPixels - (32 * density).toInt(), (520 * density).toInt())
        root.minimumWidth = width
        root.findViewById<TextView>(R.id.feedbackToastMessage).apply {
            this.text = text
            maxWidth = width - (104 * density).toInt()
        }
        root.findViewById<ImageView>(R.id.feedbackToastIcon).apply {
            setImageResource(tone.icon)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.fb_on_color))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tone.color))
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    v.alpha = 0f
                    v.translationY = 8 * density
                    v.animate().alpha(1f).translationY(0f).setDuration(180).start()
                }
            }
            override fun onViewDetachedFromWindow(v: View) { v.animate().cancel() }
        })
        return Toast(context.applicationContext).apply {
            this.duration = duration
            view = root
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, (80 * density).toInt())
        }
    }

    private fun activity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) break
            current = next
        }
        return null
    }
}
