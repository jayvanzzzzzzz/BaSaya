package com.example.basaya.controller

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.basaya.R

/**
 * Bottom navigation: a colored bubble ([underline]) slides behind the active tab's icon and
 * takes on that tab's color; the active icon turns white and hops, its label turns bold.
 */
class BottomNavController(
    private val underline: View,
    private val items: List<NavItem>,
    @param:ColorRes private val tabColors: List<Int> = listOf(
        R.color.nav_tab_home, R.color.nav_tab_progress, R.color.nav_tab_profile
    ),
    private val onSelected: (Int) -> Unit
) {
    data class NavItem(
        val container: View,
        val icon: ImageView,
        val label: TextView
    )

    private var selectedIndex = 0
    private val context = underline.context
    private val inactive = ContextCompat.getColor(context, R.color.nav_inactive)
    private val labelFont = ResourcesCompat.getFont(context, R.font.quicksand_medium)
    private var bubbleColor = colorFor(0)

    init {
        items.forEachIndexed { index, item ->
            item.container.setOnClickListener {
                if (index != selectedIndex) it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                selectTab(index, animate = true)
            }
        }
        underline.post { selectTab(0, animate = false) }
    }

    private fun colorFor(index: Int) = ContextCompat.getColor(context, tabColors[index % tabColors.size])

    fun selectTab(index: Int, animate: Boolean) {
        val container = items[index].container
        val icon = items[index].icon

        // Center the bubble on the active icon, relative to the shared FrameLayout
        val targetX = container.left + icon.left + icon.width / 2f - underline.width / 2f
        val targetY = container.top + icon.top + icon.height / 2f - underline.height / 2f
        val targetColor = colorFor(index)

        if (animate) {
            val moveX = ObjectAnimator.ofFloat(underline, View.X, underline.x, targetX)
            val moveY = ObjectAnimator.ofFloat(underline, View.Y, underline.y, targetY)
            // Squash-and-stretch while it travels
            val stretch = ObjectAnimator.ofFloat(underline, View.SCALE_X, 1f, 1.25f, 1f)
            val recolor = ValueAnimator.ofObject(ArgbEvaluator(), bubbleColor, targetColor).apply {
                addUpdateListener {
                    underline.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int)
                }
            }

            AnimatorSet().apply {
                playTogether(moveX, moveY, stretch, recolor)
                duration = 380
                interpolator = OvershootInterpolator(1.4f)
                start()
            }

            // The new icon hops
            icon.animate().cancel()
            icon.scaleX = 0.7f
            icon.scaleY = 0.7f
            icon.translationY = 0f
            icon.animate().scaleX(1f).scaleY(1f).setDuration(380)
                .setInterpolator(OvershootInterpolator(3f)).start()
        } else {
            underline.x = targetX
            underline.y = targetY
            underline.backgroundTintList = ColorStateList.valueOf(targetColor)
        }
        bubbleColor = targetColor

        items.forEachIndexed { i, item ->
            val active = i == index
            item.container.isSelected = active
            item.icon.imageTintList = ColorStateList.valueOf(if (active) 0xFFFFFFFF.toInt() else inactive)
            item.label.setTextColor(if (active) colorFor(i) else inactive)
            item.label.setTypeface(labelFont, if (active) Typeface.BOLD else Typeface.NORMAL)
        }

        selectedIndex = index
        onSelected(index)
    }
}
