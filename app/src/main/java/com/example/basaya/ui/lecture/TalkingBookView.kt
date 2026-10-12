package com.example.basaya.ui.lecture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The lecture narrator: a blue book whose mouth moves while audio plays.
 *
 * Drawn in a 240 x 260 design space (same geometry as the approved preview) and scaled
 * to fit. Mouth openness is a continuous 0..1 value eased every frame, so talking and
 * closing are smooth rather than flipping between two images. The book also blinks
 * every few seconds while idle.
 */
class TalkingBookView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var talking = false
    private var mouthOpen = 0f      // rendered openness, 0 = closed smile
    private var talkAmount = 0f     // eased 0..1, drives the bobbing
    private var blinkStartMs = -1L
    private var lastFrameMs = 0L
    private var clockSec = 0.0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val coverRect = RectF(40f, 26f, 190f, 226f)
    private val coverGradient = LinearGradient(
        0f, 26f, 0f, 226f, COVER_TOP, COVER_BOTTOM, Shader.TileMode.CLAMP
    )
    private val spinePath = path("M60 26 H62 V226 H60 A20 20 0 0 1 40 206 V46 A20 20 0 0 1 60 26 Z")
    private val pageLinesPath = path("M194 52 V212 M188 50 V214")
    private val leftArmPath = path("M46 148 Q26 160 28 182")
    private val rightArmPath = path("M188 132 Q212 120 214 96")
    private val glossPath = path("M150 40 Q176 44 178 72")
    private val ribbonPath = path("M146 222 V250 L154 243 L162 250 V222 Z")
    private val browsPath = path("M90 76 Q102 68 114 74 M140 74 Q152 68 164 76")
    private val mouthPath = Path()
    private val lashPath = Path()
    private val rect = RectF()

    private val blinkRunnable = Runnable {
        blinkStartMs = SystemClock.uptimeMillis()
        lastFrameMs = 0L
        postInvalidateOnAnimation()
    }

    /** Start or stop talking. Stopping eases the mouth closed instead of snapping. */
    fun setTalking(value: Boolean) {
        if (talking == value) return
        talking = value
        lastFrameMs = 0L
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension(
            resolveSize((DESIGN_W * 0.45f * density).toInt(), widthMeasureSpec),
            resolveSize((DESIGN_H * 0.45f * density).toInt(), heightMeasureSpec)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleBlink()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinkRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val keepAnimating = step()

        val scale = min(width / DESIGN_W, height / DESIGN_H)
        val bob = -abs(sin(clockSec * 2 * PI * 1.5)).toFloat() * 5f * talkAmount

        canvas.save()
        canvas.translate((width - DESIGN_W * scale) / 2f, (height - DESIGN_H * scale) / 2f)
        canvas.scale(scale, scale)

        drawShadow(canvas, bob)
        canvas.translate(0f, bob)
        drawBody(canvas)
        drawFace(canvas)
        canvas.restore()

        if (keepAnimating) postInvalidateOnAnimation()
    }

    /** Advances the animation clock; returns true while another frame is needed. */
    private fun step(): Boolean {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceAtMost(0.05f)
        lastFrameMs = now
        clockSec += dt

        val target = if (talking) speechOpenness(clockSec) else 0f
        // Opening follows speech quickly; closing settles in about 0.15 s.
        val rate = if (talking) 22f else 20f
        mouthOpen += (target - mouthOpen) * (1f - exp(-dt * rate))
        talkAmount += ((if (talking) 1f else 0f) - talkAmount) * (1f - exp(-dt * 6f))

        if (!talking && mouthOpen < 0.004f) mouthOpen = 0f
        if (!talking && talkAmount < 0.004f) talkAmount = 0f

        val blinking = blinkStartMs >= 0 && now - blinkStartMs < BLINK_MS
        if (blinkStartMs >= 0 && !blinking) {
            blinkStartMs = -1L
            scheduleBlink()
        }

        val settling = talking || mouthOpen > 0f || talkAmount > 0f || blinking
        if (!settling) lastFrameMs = 0L
        return settling
    }

    /** Syllable-like rhythm with a slower envelope and short pauses, so it never looks looped. */
    private fun speechOpenness(t: Double): Float {
        val syllables = 0.55 + 0.45 * sin(t * 2 * PI * 3.1)
        val phrase = 0.65 + 0.35 * sin(t * 2 * PI * 0.83 + 1.3)
        val pause = if (sin(t * 2 * PI * 0.37) > 0.88) 0.25 else 1.0
        return (syllables * phrase * pause).toFloat().coerceIn(0f, 1f)
    }

    private fun scheduleBlink() {
        removeCallbacks(blinkRunnable)
        if (isAttachedToWindow) postDelayed(blinkRunnable, Random.nextLong(3000, 5200))
    }

    private fun blinkAmount(): Float {
        if (blinkStartMs < 0) return 0f
        val p = ((SystemClock.uptimeMillis() - blinkStartMs) / BLINK_MS.toFloat()).coerceIn(0f, 1f)
        return sin(p * PI).toFloat()
    }

    private fun drawShadow(canvas: Canvas, bob: Float) {
        // Shadow tightens slightly as the book lifts.
        val shrink = 1f + bob / 60f
        fill.shader = null
        fill.color = Color.argb(26, 0, 0, 0)
        rect.set(122f - 72f * shrink, 240f, 122f + 72f * shrink, 256f)
        canvas.drawOval(rect, fill)
    }

    private fun drawBody(canvas: Canvas) {
        fill.shader = null

        fill.color = FOOT
        oval(canvas, 94f, 236f, 18f, 9f)
        oval(canvas, 152f, 236f, 18f, 9f)

        fill.color = BACK_COVER
        rect.set(54f, 34f, 204f, 230f)
        canvas.drawRoundRect(rect, 18f, 18f, fill)
        fill.color = PAGES
        rect.set(60f, 40f, 200f, 224f)
        canvas.drawRoundRect(rect, 12f, 12f, fill)
        stroke.color = PAGE_LINE
        stroke.strokeWidth = 2.5f
        canvas.drawPath(pageLinesPath, stroke)

        drawArm(canvas, leftArmPath, 28f, 186f)

        fill.shader = coverGradient
        canvas.drawRoundRect(coverRect, 20f, 20f, fill)
        fill.shader = null
        fill.color = SPINE
        canvas.drawPath(spinePath, fill)
        fill.color = Color.argb(46, 255, 255, 255)
        rect.set(62f, 26f, 67f, 226f)
        canvas.drawRect(rect, fill)
        stroke.color = Color.argb(90, 255, 255, 255)
        stroke.strokeWidth = 6f
        canvas.drawPath(glossPath, stroke)

        fill.color = RIBBON
        canvas.drawPath(ribbonPath, fill)
        fill.color = RIBBON_SHADE
        rect.set(146f, 222f, 162f, 227f)
        canvas.drawRect(rect, fill)

        drawArm(canvas, rightArmPath, 214f, 90f)
    }

    private fun drawArm(canvas: Canvas, arm: Path, handX: Float, handY: Float) {
        stroke.color = ARM
        stroke.strokeWidth = 10f
        canvas.drawPath(arm, stroke)
        fill.color = HAND
        canvas.drawCircle(handX, handY, 11f, fill)
        stroke.color = INK
        stroke.strokeWidth = 2.5f
        canvas.drawCircle(handX, handY, 11f, stroke)
    }

    private fun drawFace(canvas: Canvas) {
        stroke.color = INK
        stroke.strokeWidth = 4f
        canvas.drawPath(browsPath, stroke)

        val blink = blinkAmount()
        drawEye(canvas, 104f, blink)
        drawEye(canvas, 150f, blink)

        fill.color = CHEEK
        oval(canvas, 86f, 132f, 10f, 6f)
        oval(canvas, 168f, 132f, 10f, 6f)

        drawMouth(canvas, mouthOpen)
    }

    private fun drawEye(canvas: Canvas, cx: Float, blink: Float) {
        val squash = 1f - blink
        if (squash < 0.25f) {
            // Fully shut: a soft lash curve reads better than a squashed sliver.
            lashPath.reset()
            lashPath.moveTo(cx - 16f, 102f)
            lashPath.quadTo(cx, 110f, cx + 16f, 102f)
            stroke.color = INK
            stroke.strokeWidth = 3.5f
            canvas.drawPath(lashPath, stroke)
            return
        }

        canvas.save()
        canvas.scale(1f, squash, cx, 102f)
        fill.color = Color.WHITE
        oval(canvas, cx, 102f, 17f, 20f)
        fill.color = INK
        canvas.drawCircle(cx + 2f, 106f, 9.5f, fill)
        fill.color = Color.WHITE
        canvas.drawCircle(cx - 1.5f, 101.5f, 3.6f, fill)
        canvas.drawCircle(cx + 5f, 110f, 1.6f, fill)
        stroke.color = INK
        stroke.strokeWidth = 3.5f
        rect.set(cx - 17f, 82f, cx + 17f, 122f)
        canvas.drawOval(rect, stroke)
        canvas.restore()
    }

    /** t = 0 is the closed smile; the lower lip drops and widens as t grows. */
    private fun drawMouth(canvas: Canvas, t: Float) {
        val left = 106f - 3f * t
        val right = 148f + 3f * t
        mouthPath.reset()
        mouthPath.moveTo(left, 140f)
        mouthPath.quadTo(127f, 150f - 2f * t, right, 140f)
        mouthPath.quadTo(127f, 150f + 38f * t, left, 140f)
        mouthPath.close()

        if (t > 0.01f) {
            fill.color = MOUTH
            canvas.drawPath(mouthPath, fill)
            canvas.save()
            canvas.clipPath(mouthPath)
            fill.color = Color.WHITE
            rect.set(100f, 139f + 2f * t, 154f, 139f + 8f * t)
            canvas.drawRect(rect, fill)
            fill.color = TONGUE
            oval(canvas, 127f, 147f + 17f * t, 12f + 3f * t, 10f * t)
            canvas.restore()
        }

        stroke.color = INK
        stroke.strokeWidth = 4f
        canvas.drawPath(mouthPath, stroke)
    }

    private fun oval(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawOval(rect, fill)
    }

    private fun path(data: String): Path = PathParser.createPathFromPathData(data)

    private companion object {
        const val DESIGN_W = 240f
        const val DESIGN_H = 260f
        const val BLINK_MS = 150L

        val COVER_TOP = Color.parseColor("#4F8DFF")
        val COVER_BOTTOM = Color.parseColor("#2D63D7")
        val BACK_COVER = Color.parseColor("#1D47A8")
        val SPINE = Color.parseColor("#2350BF")
        val PAGES = Color.parseColor("#FFF4D6")
        val PAGE_LINE = Color.parseColor("#E8D7A8")
        val RIBBON = Color.parseColor("#FFC94D")
        val RIBBON_SHADE = Color.parseColor("#E9A92A")
        val INK = Color.parseColor("#1B2A4A")
        val MOUTH = Color.parseColor("#8E2338")
        val TONGUE = Color.parseColor("#FF7A90")
        val CHEEK = Color.parseColor("#E6FF9DB5")
        val ARM = Color.parseColor("#2D63D7")
        val HAND = Color.parseColor("#4F8DFF")
        val FOOT = Color.parseColor("#1D47A8")
    }
}
