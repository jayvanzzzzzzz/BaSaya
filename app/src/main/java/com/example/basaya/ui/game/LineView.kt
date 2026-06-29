package com.example.basaya.ui.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.example.basaya.R

class LineView(context: Context) : View(context) {

    val selectedPoints = mutableListOf<Pair<Float, Float>>()

    var fingerX = 0f
    var fingerY = 0f

    private val paint = Paint().apply {
        strokeWidth = 12f
        isAntiAlias = true
        color = context.getColor(R.color.main_blue)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw lines between selected letters
        for (i in 0 until selectedPoints.size - 1) {

            val start = selectedPoints[i]
            val end = selectedPoints[i + 1]

            canvas.drawLine(
                start.first,
                start.second,
                end.first,
                end.second,
                paint
            )
        }

        // Draw line from last selected letter to finger
        if (selectedPoints.isNotEmpty()) {

            val last = selectedPoints.last()

            canvas.drawLine(
                last.first,
                last.second,
                fingerX,
                fingerY,
                paint
            )
        }
    }
}