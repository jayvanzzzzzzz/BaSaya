package com.example.basaya.controller

import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.basaya.R

class BottomNavController(
    private val indicator: View,
    private val items: List<NavItem>,
    private val onSelected: (Int) -> Unit
) {
    data class NavItem(val container: LinearLayout, val icon: ImageView, val label: TextView)

    private var selectedIndex = 0

    init {
        items.forEachIndexed { index, item ->
            item.container.setOnClickListener { selectTab(index, animate = true) }
        }
        indicator.post { selectTab(0, animate = false) }
    }

    fun selectTab(index: Int, animate: Boolean) {
        val target = items[index].container
        val targetX = target.left + (target.width - indicator.width) / 2f

        if (animate) {
            indicator.animate()
                .x(targetX)
                .setDuration(260)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        } else {
            indicator.x = targetX
        }

        items.forEachIndexed { i, item ->
            val active = i == index
            val color = if (active)
                ContextCompat.getColor(item.icon.context, R.color.nav_item_color_active)
            else
                ContextCompat.getColor(item.icon.context, R.color.nav_item_color)
            item.icon.setColorFilter(color)
            item.label.setTextColor(color)
            item.container.animate().scaleX(if (active) 1.08f else 1f)
                .scaleY(if (active) 1.08f else 1f).setDuration(200).start()
        }

        selectedIndex = index
        onSelected(index)
    }
}