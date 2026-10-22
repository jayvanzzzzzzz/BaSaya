package com.example.basaya.ui.lecture

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.example.basaya.R
import kotlin.math.max
import kotlin.math.min

/**
 * A lecture sentence the child can read along with: while its audio plays, a soft pill glides
 * behind each word as it is spoken and a faint trail grows under the words already read (the
 * website's student/readAlong.js does the same). The letters never move or change color, so
 * the sentence stays easy to read. Words are the text
 * split on spaces, the split the website uses for the wordStarts saved with each sentence's audio.
 */
class ReadAlongTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    companion object {
        /**
         * Estimated start of each word (seconds), for audio saved without timings: the sound
         * is shared out by word length, with a little silence first and last, and a pause
         * after commas and full stops.
         */
        fun estimateStarts(words: List<String>, duration: Double): List<Double> {
            val lead = min(0.12, duration * 0.04)
            val tail = min(0.35, duration * 0.08)
            val weights = words.mapIndexed { i, word ->
                val letters = word.count { it.isLetterOrDigit() }
                val trimmed = word.trimEnd('"', '\'', '”', '’', ')', ']')
                val pause = when {
                    i == words.lastIndex -> 0
                    trimmed.endsWith('.') || trimmed.endsWith('!') || trimmed.endsWith('?') || trimmed.endsWith('…') -> 6
                    trimmed.endsWith(',') || trimmed.endsWith(';') || trimmed.endsWith(':') || trimmed.endsWith('—') -> 3
                    else -> 0
                }
                2.0 + letters + pause
            }
            val total = weights.sum().takeIf { it > 0 } ?: 1.0
            val speaking = max(0.0, duration - lead - tail)
            var before = 0.0
            return weights.map { weight ->
                val start = lead + speaking * before / total
                before += weight
                start
            }
        }
    }

    private val density = resources.displayMetrics.density

    /** [start, end) of each word in the text. */
    private var wordRanges: List<Pair<Int, Int>> = emptyList()
    private var reading = false
    private var current = -1

    private val pillStart = ContextCompat.getColor(context, R.color.lecture_bubble)
    private val pillEnd = ContextCompat.getColor(context, R.color.lecture_bubble_end)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = ContextCompat.getColor(context, R.color.lecture_bubble_stroke)
    }
    private val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lecture_bubble_lip)
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lecture_trail)
    }

    /** The pill as drawn (mid-glide it is between two words). */
    private val pill = RectF()
    private val lip = RectF()
    private val trail = RectF()
    private val glideFrom = RectF()
    private val glideTo = RectF()
    /** 0..1 through the current glide; the newest piece of trail grows with it. */
    private var glide = 1f
    /** 0..1: how visible the pill is (it fades in on the first word). */
    private var pillAlpha = 0f

    private val glideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 220
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            val t = it.animatedValue as Float
            glide = t
            pill.set(
                glideFrom.left + (glideTo.left - glideFrom.left) * t,
                glideFrom.top + (glideTo.top - glideFrom.top) * t,
                glideFrom.right + (glideTo.right - glideFrom.right) * t,
                glideFrom.bottom + (glideTo.bottom - glideFrom.bottom) * t
            )
            invalidate()
        }
    }
    private val fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 140
        addUpdateListener {
            pillAlpha = it.animatedValue as Float
            invalidate()
        }
    }

    /** Shows the sentence; [text] may carry styling (bold, italics) from the lesson. */
    fun setSentence(text: CharSequence) {
        setText(text)
        wordRanges = Regex("\\S+").findAll(text).map { it.range.first to it.range.last + 1 }.toList()
    }

    val words: List<String>
        get() = wordRanges.map { (start, end) -> text.substring(start, end) }

    /** Gets ready to follow the audio; the pill appears on the first word once it is spoken. */
    fun beginReading() {
        reading = true
        current = -1
    }

    /** Moves the pill behind word [index] (the word being spoken now). */
    fun showWord(index: Int) {
        if (!reading || index == current || index !in wordRanges.indices) return
        val target = RectF()
        if (!wordRect(index, target)) return
        glideAnimator.cancel()
        if (current == -1) {
            // The first word: the pill fades in where it is.
            pill.set(target)
            glide = 1f
            fadeAnimator.start()
        } else {
            glideFrom.set(pill)
            glideTo.set(target)
            glide = 0f
            glideAnimator.start()
        }
        current = index
        invalidate()
    }

    /** Hides the pill (the audio ended or stopped). */
    fun endReading() {
        if (!reading) return
        reading = false
        current = -1
        glideAnimator.cancel()
        fadeAnimator.cancel()
        pillAlpha = 0f
        invalidate()
    }

    /** Where word [index] sits in this view, padded to pill size. False before layout. */
    private fun wordRect(index: Int, out: RectF): Boolean {
        val layout = layout ?: return false
        val (start, end) = wordRanges[index]
        val line = layout.getLineForOffset(start)
        val left = layout.getPrimaryHorizontal(start)
        val right = if (layout.getLineForOffset(end) == line) layout.getPrimaryHorizontal(end) else layout.getLineRight(line)
        val baseline = layout.getLineBaseline(line).toFloat()
        val metrics = paint.fontMetrics
        val padX = 6 * density
        val padY = 2 * density
        out.set(left - padX, baseline + metrics.ascent - padY, right + padX, baseline + metrics.descent + padY)
        out.offset(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (reading && current >= 0) {
            drawTrail(canvas)
            val radius = pill.height() / 2
            val alpha = (255 * pillAlpha).toInt()
            // A thin raised edge under the pill, like the app's buttons.
            lip.set(pill)
            lip.offset(0f, 2 * density)
            lipPaint.alpha = alpha
            canvas.drawRoundRect(lip, radius, radius, lipPaint)
            pillPaint.shader = LinearGradient(
                pill.left, 0f, pill.right, 0f, pillStart, pillEnd, Shader.TileMode.CLAMP
            )
            pillPaint.alpha = alpha
            pillStroke.alpha = alpha
            canvas.drawRoundRect(pill, radius, radius, pillPaint)
            canvas.drawRoundRect(pill, radius, radius, pillStroke)
        }
        super.onDraw(canvas)
    }

    /**
     * A faint line under the words already read. Each piece reaches across the space to the
     * next word on its line, so it reads as one line; the newest piece grows with the glide.
     */
    private fun drawTrail(canvas: Canvas) {
        val layout = layout ?: return
        val thickness = 3 * density
        val drop = paint.fontMetrics.descent * 0.6f
        for (i in 0 until current) {
            val (start, end) = wordRanges[i]
            val line = layout.getLineForOffset(start)
            val nextStart = wordRanges[i + 1].first
            val left = layout.getPrimaryHorizontal(start)
            val full = if (layout.getLineForOffset(nextStart) == line) layout.getPrimaryHorizontal(nextStart)
            else layout.getPrimaryHorizontal(end)
            val right = if (i == current - 1) left + (full - left) * glide.coerceIn(0f, 1f) else full
            val y = layout.getLineBaseline(line) + drop
            trail.set(left, y, right, y + thickness)
            trail.offset(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
            canvas.drawRoundRect(trail, thickness / 2, thickness / 2, trailPaint)
        }
    }

    override fun onDetachedFromWindow() {
        glideAnimator.cancel()
        fadeAnimator.cancel()
        super.onDetachedFromWindow()
    }
}
