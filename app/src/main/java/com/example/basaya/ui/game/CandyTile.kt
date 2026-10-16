package com.example.basaya.ui.game

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.content.ContextCompat
import com.example.basaya.R

/** Builds the 3D "candy" tile used for crossword cells: a darker lip under a bright face. */
internal object CandyTile {

    data class Colors(val fill: Int, val lip: Int)

    /** One color per found word; all are dark enough for white bold letters. */
    val PALETTE = listOf(
        Colors(Color.parseColor("#F25C54"), Color.parseColor("#C9413A")), // coral
        Colors(Color.parseColor("#4F7CEC"), Color.parseColor("#3559C2")), // blue
        Colors(Color.parseColor("#14A396"), Color.parseColor("#0C7C72")), // teal
        Colors(Color.parseColor("#8E5CF0"), Color.parseColor("#6C3FC8")), // purple
        Colors(Color.parseColor("#E46F0E"), Color.parseColor("#B95606")), // orange
        Colors(Color.parseColor("#E8508F"), Color.parseColor("#BE3570")), // pink
        Colors(Color.parseColor("#2FA84F"), Color.parseColor("#1F8139"))  // green
    )

    /** An unsolved cell; follows light/dark mode. */
    fun empty(context: Context) = Colors(
        ContextCompat.getColor(context, R.color.cw_tile_empty),
        ContextCompat.getColor(context, R.color.cw_tile_empty_lip)
    )

    fun create(colors: Colors, sizePx: Int): Drawable {
        val radius = sizePx * 0.22f
        val lip = (sizePx * 0.09f).toInt().coerceAtLeast(2)

        val base = GradientDrawable().apply {
            cornerRadius = radius
            setColor(colors.lip)
        }
        val face = GradientDrawable().apply {
            cornerRadius = radius
            setColor(colors.fill)
        }
        return LayerDrawable(arrayOf(base, face)).apply {
            setLayerInset(0, 0, lip, 0, 0)
            setLayerInset(1, 0, 0, 0, lip)
        }
    }

    fun forWord(wordIndex: Int): Colors = PALETTE[wordIndex % PALETTE.size]
}
