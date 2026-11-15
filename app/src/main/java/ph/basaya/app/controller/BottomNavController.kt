package ph.basaya.app.controller

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import ph.basaya.app.R

/** A moving destination ticket with a tactile press and a short icon settle. */
class BottomNavController(
    private val underline: View,
    private val items: List<NavItem>,
    private val initialIndex: Int = 0,
    @param:ColorRes private val tabColors: List<Int> = listOf(
        R.color.nav_tab_home, R.color.nav_tab_progress, R.color.nav_tab_chat, R.color.nav_tab_profile
    ),
    private val onSelected: (Int) -> Unit
) {
    data class NavItem(val container: View, val icon: ImageView, val label: TextView)

    private var selectedIndex = initialIndex
    private val context = underline.context
    private val inactive = ContextCompat.getColor(context, R.color.nav_inactive)
    private val onTicket = ContextCompat.getColor(context, R.color.nav_on_ticket)
    private val well = ContextCompat.getColor(context, R.color.nav_well)
    private var ticketColor = colorFor(initialIndex)
    private var transition: AnimatorSet? = null
    private val gap = 4f * context.resources.displayMetrics.density

    init {
        items.forEachIndexed { index, item ->
            item.container.setOnClickListener {
                if (index != selectedIndex) it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                selectTab(index, animate = true)
            }
        }
        // Keep the ticket aligned after rotation, font-size changes and inset updates.
        (underline.parent as View).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            placeTicket(selectedIndex, animate = false)
        }
        underline.post { selectTab(initialIndex, animate = false) }
    }

    private fun colorFor(index: Int) = ContextCompat.getColor(context, tabColors[index % tabColors.size])

    private fun motionEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    private fun placeTicket(index: Int, animate: Boolean) {
        val tab = items[index].container
        if (tab.width == 0 || tab.height == 0) return
        val bounds = Rect(0, 0, tab.width, tab.height)
        (underline.parent as ViewGroup).offsetDescendantRectToMyCoords(tab, bounds)
        val width = (bounds.width() - gap * 2).toInt()
        val height = bounds.height()
        if (underline.layoutParams.width != width || underline.layoutParams.height != height) {
            underline.layoutParams = underline.layoutParams.apply {
                this.width = width
                this.height = height
            }
        }
        val targetX = bounds.left + gap
        val targetY = bounds.top.toFloat()
        val targetColor = colorFor(index)
        transition?.cancel()
        if (animate && motionEnabled()) {
            val recolor = ValueAnimator.ofObject(ArgbEvaluator(), ticketColor, targetColor).apply {
                addUpdateListener {
                    ticketColor = it.animatedValue as Int
                    underline.backgroundTintList = ColorStateList.valueOf(ticketColor)
                }
            }
            transition = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(underline, View.X, underline.x, targetX),
                    ObjectAnimator.ofFloat(underline, View.Y, underline.y, targetY),
                    recolor
                )
                duration = 240
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }
        } else {
            underline.x = targetX
            underline.y = targetY
            ticketColor = targetColor
            underline.backgroundTintList = ColorStateList.valueOf(targetColor)
        }
    }

    fun selectTab(index: Int, animate: Boolean) {
        if (index !in items.indices) return
        val changed = selectedIndex != index
        selectedIndex = index
        placeTicket(index, animate && changed)
        items.forEachIndexed { i, item ->
            val active = i == index
            item.container.isSelected = active
            item.icon.animate().cancel()
            item.icon.scaleX = 1f
            item.icon.scaleY = 1f
            item.icon.rotation = 0f
            item.icon.imageTintList = ColorStateList.valueOf(if (active) onTicket else inactive)
            item.icon.backgroundTintList = ColorStateList.valueOf(if (active) 0x22FFFFFF else well)
            item.label.setTextColor(if (active) onTicket else inactive)
        }
        if (animate && changed && motionEnabled()) {
            items[index].icon.apply {
                scaleX = 0.9f
                scaleY = 0.9f
                rotation = -8f
                animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(240)
                    .setInterpolator(OvershootInterpolator(1.2f)).start()
            }
        }
        onSelected(index)
    }
}
