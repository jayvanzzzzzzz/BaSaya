package com.example.basaya.controller

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.basaya.R

class BottomNavController(
    private val underline: View,
    private val items: List<NavItem>,
    private val onSelected: (Int) -> Unit
) {
    data class NavItem(
        val container: View,
        val icon: ImageView,
        val label: TextView
    )

    private var selectedIndex = 0

    init {
        items.forEachIndexed { index, item ->
            item.container.setOnClickListener { selectTab(index, animate = true) }
        }
        underline.post { selectTab(0, animate = false) }
    }

    fun selectTab(index: Int, animate: Boolean) {
        val container = items[index].container
        val icon = items[index].icon
        val label = items[index].label

        // Horizontal: match the label's left edge + width, relative to the shared FrameLayout
        val targetX = (container.left + label.left).toFloat()
        val targetWidth = label.width

        // Vertical: sit just below the icon, same gap the earlier per-item underline used
        val gapPx = (4 * underline.resources.displayMetrics.density)
        val targetY = (container.top + icon.bottom).toFloat() + gapPx

        if (animate) {
            val moveX = ObjectAnimator.ofFloat(underline, View.X, underline.x, targetX)
            val moveY = ObjectAnimator.ofFloat(underline, View.Y, underline.y, targetY)

            val resizeWidth = ValueAnimator.ofInt(
                underline.width.takeIf { it > 0 } ?: targetWidth,
                targetWidth
            ).apply {
                addUpdateListener {
                    underline.layoutParams = underline.layoutParams.apply {
                        width = it.animatedValue as Int
                    }
                }
            }

            AnimatorSet().apply {
                playTogether(moveX, moveY, resizeWidth)
                duration = 260
                interpolator = OvershootInterpolator(1.0f)
                start()
            }
        } else {
            underline.x = targetX
            underline.y = targetY
            underline.layoutParams = underline.layoutParams.apply { width = targetWidth }
            underline.requestLayout()
        }

        items.forEachIndexed { i, item ->
            val active = i == index
            val color = ContextCompat.getColor(
                item.icon.context,
                if (active) R.color.nav_item_color_active else R.color.nav_item_color
            )
            item.icon.setColorFilter(color)
            item.label.setTextColor(color)
        }

        selectedIndex = index
        onSelected(index)
    }
}