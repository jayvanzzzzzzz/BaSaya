package ph.basaya.app.ui.feedback

import android.animation.AnimatorInflater
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import ph.basaya.app.R

/** Short, finite feedback shared only by the dictionary and learning inbox. */
object WordInboxMotion {
    fun enabled(view: View): Boolean =
        Settings.Global.getFloat(view.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    fun press(view: View) {
        view.stateListAnimator = AnimatorInflater.loadStateListAnimator(view.context, R.animator.wi_press)
    }

    fun reveal(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.translationY = 0f
        if (!enabled(view)) return
        view.alpha = 0f
        view.translationY = 8f * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
    }
}
