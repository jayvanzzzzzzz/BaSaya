package ph.basaya.app.ui.pronunciation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import ph.basaya.app.R
import kotlin.math.abs
import kotlin.math.sin

/** Quiet baseline becomes a live, symmetrical voice signature while the mic is held. */
class MicWaveformView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pr_teal)
        strokeCap = Paint.Cap.ROUND
    }
    private var level = 0f
    private var phase = 0f
    fun setLevel(value: Float, time: Float) { level = value; phase = time; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = width / 11f
        ink.strokeWidth = step * 0.44f
        for (i in 0..8) {
            val envelope = 1f - abs(i - 4) / 5f
            val motion = 0.6f + 0.4f * abs(sin(phase * 7f + i * 0.8f))
            val half = height * (0.07f + level * 0.37f * envelope * motion)
            val x = width / 2f + (i - 4) * step
            canvas.drawLine(x, height / 2f - half, x, height / 2f + half, ink)
        }
    }
}
