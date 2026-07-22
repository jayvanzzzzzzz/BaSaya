package com.example.basaya.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class DotGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD7DEE8.toInt()
        style = Paint.Style.FILL
    }

    private val spacingPx = 22f * resources.displayMetrics.density
    private val dotRadiusPx = 1.4f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var y = spacingPx / 2
        while (y < height) {
            var x = spacingPx / 2
            while (x < width) {
                canvas.drawCircle(x, y, dotRadiusPx, dotPaint)
                x += spacingPx
            }
            y += spacingPx
        }
    }
}