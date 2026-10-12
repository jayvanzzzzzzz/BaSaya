package com.example.basaya.ui.pronunciation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The pronunciation buddy: a coral book wearing headphones.
 *
 * Unlike the lecture's blue narrator, this book reacts to the child: it talks while the
 * word plays, cups its headphones and pulses with the child's voice while listening,
 * jumps with sparkles when the word is right, and gives a thumbs-up when it isn't.
 * Every pose is a set of eased values, so changes between moods are always smooth.
 */
class PronunciationBookView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mood { IDLE, TALKING, LISTENING, THINKING, HAPPY, CHEER }

    var mood: Mood = Mood.IDLE
        private set

    private var moodStartSec = 0.0
    @Volatile private var voiceLevelTarget = 0f

    // Eased animation state
    private var clockSec = 0.0
    private var lastFrameMs = 0L
    private var mouthOpen = 0f
    private var talkAmount = 0f
    private var listenAmount = 0f
    private var thinkAmount = 0f
    private var smileAmount = 0f
    private var happyEyes = 0f
    private var voiceLevel = 0f
    private var lookX = 0f
    private var lookY = 0f
    private var browLeft = 0f
    private var browRight = 0f
    private var thumbAmount = 0f
    private val leftHand = PointF(30f, 224f)
    private val rightHand = PointF(206f, 224f)
    private var blinkStartMs = -1L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val coverGradient = LinearGradient(0f, 54f, 0f, 250f, COVER_TOP, COVER_BOTTOM, Shader.TileMode.CLAMP)
    private val rect = RectF()
    private val spineTopArc = RectF(44f, 54f, 88f, 98f)
    private val spineBottomArc = RectF(44f, 206f, 88f, 250f)
    private val path = Path()

    private val blinkRunnable = Runnable {
        blinkStartMs = SystemClock.uptimeMillis()
        scheduleBlink()
    }

    /** Switch mood; HAPPY and CHEER replay their jump each time they are set. */
    fun setMood(newMood: Mood) {
        if (newMood == mood && newMood != Mood.HAPPY && newMood != Mood.CHEER) return
        mood = newMood
        moodStartSec = clockSec
        if (newMood != Mood.LISTENING) voiceLevelTarget = 0f
        invalidate()
    }

    /** Child's voice loudness 0..1 while listening. Safe to call from any thread. */
    fun setVoiceLevel(level: Float) {
        voiceLevelTarget = level.coerceIn(0f, 1f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension(
            resolveSize((DESIGN_W * 0.7f * density).toInt(), widthMeasureSpec),
            resolveSize((DESIGN_H * 0.7f * density).toInt(), heightMeasureSpec)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameMs = 0L
        scheduleBlink()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinkRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        step()

        val scale = min(width / DESIGN_W, height / DESIGN_H)
        canvas.save()
        canvas.translate((width - DESIGN_W * scale) / 2f, (height - DESIGN_H * scale) / 2f)
        canvas.scale(scale, scale)

        val (lift, tilt, squash) = bodyMotion()
        drawShadow(canvas, lift)

        canvas.save()
        canvas.translate(0f, -lift)
        canvas.rotate(tilt, CENTER_X, GROUND_Y)
        canvas.scale(1f / squash, squash, CENTER_X, GROUND_Y)
        drawFeet(canvas)
        drawSoundWaves(canvas)
        drawBook(canvas)
        drawHeadphones(canvas)
        drawFace(canvas)
        drawArms(canvas)
        canvas.restore()

        drawSparkles(canvas)
        drawThinkingDots(canvas)
        canvas.restore()

        // The buddy is always a little alive (breathing, blinking), so keep drawing while shown.
        if (isShown) postInvalidateOnAnimation()
    }

    // ───────────────────────────── Animation state ─────────────────────────────

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceAtMost(0.05f)
        lastFrameMs = now
        clockSec += dt
        val t = clockSec
        val inMood = (t - moodStartSec).toFloat()

        // Transient moods settle back to a calm pose on their own.
        val celebrating = mood == Mood.HAPPY && inMood < 2.2f
        val cheering = mood == Mood.CHEER && inMood < 2.2f

        voiceLevel = ease(voiceLevel, if (mood == Mood.LISTENING) voiceLevelTarget else 0f, 14f, dt)
        talkAmount = ease(talkAmount, if (mood == Mood.TALKING) 1f else 0f, 7f, dt)
        listenAmount = ease(listenAmount, if (mood == Mood.LISTENING) 1f else 0f, 6f, dt)
        thinkAmount = ease(thinkAmount, if (mood == Mood.THINKING) 1f else 0f, 6f, dt)
        happyEyes = ease(happyEyes, if (celebrating || cheering) 1f else 0f, 10f, dt)
        smileAmount = ease(smileAmount, if (celebrating) 1f else if (cheering) 0.65f else 0f, 9f, dt)
        thumbAmount = ease(thumbAmount, if (cheering) 1f else 0f, 9f, dt)

        val mouthTarget = when (mood) {
            Mood.TALKING -> speechOpenness(t)
            Mood.LISTENING -> 0.16f + voiceLevel * 0.1f // a curious little "oh"
            else -> 0f
        }
        mouthOpen = ease(mouthOpen, mouthTarget, if (mood == Mood.TALKING) 22f else 14f, dt)

        val (lx, ly) = when (mood) {
            Mood.LISTENING -> 3.5f to -1.5f
            Mood.THINKING -> -3f to -4f
            else -> (sin(t * 0.7) * 1.2).toFloat() to 0f
        }
        lookX = ease(lookX, lx, 6f, dt)
        lookY = ease(lookY, ly, 6f, dt)

        browLeft = ease(browLeft, if (cheering || celebrating) 5f else if (mood == Mood.THINKING) 3f else 0f, 8f, dt)
        browRight = ease(browRight, if (cheering || celebrating || mood == Mood.LISTENING) 5f else 0f, 8f, dt)

        val wave = sin(t * 2 * PI * 2.2).toFloat()
        val (left, right) = when {
            celebrating -> PointF(20f, 112f + wave * 8f) to PointF(216f, 112f - wave * 8f)
            cheering -> PointF(34f, 214f) to PointF(220f, 150f + abs(wave) * -6f)
            mood == Mood.LISTENING -> PointF(30f, 222f) to PointF(218f, 140f)
            mood == Mood.THINKING -> PointF(30f, 222f) to PointF(140f, 200f)
            mood == Mood.TALKING -> PointF(30f, 220f) to PointF(216f, 182f + sin(t * 2 * PI * 1.3).toFloat() * 10f)
            else -> PointF(30f, 224f + sin(t * 2 * PI * 0.5).toFloat() * 2f) to
                PointF(206f, 224f - sin(t * 2 * PI * 0.5).toFloat() * 2f)
        }
        leftHand.x = ease(leftHand.x, left.x, 9f, dt)
        leftHand.y = ease(leftHand.y, left.y, 9f, dt)
        rightHand.x = ease(rightHand.x, right.x, 9f, dt)
        rightHand.y = ease(rightHand.y, right.y, 9f, dt)
    }

    /** Lift (up is positive), tilt in degrees, and vertical squash for the whole body. */
    private fun bodyMotion(): Triple<Float, Float, Float> {
        val t = clockSec
        val inMood = (t - moodStartSec).toFloat()
        val breathe = 1f + 0.012f * sin(t * 2 * PI * 0.5).toFloat()

        var lift = abs(sin(t * 2 * PI * 1.6)).toFloat() * 5f * talkAmount
        var tilt = 5f * listenAmount + 3f * thinkAmount * sin(t * 2 * PI * 0.6).toFloat()
        var squash = breathe * (1f + 0.04f * voiceLevel)

        if (mood == Mood.HAPPY && inMood < 1.6f) {
            // Three bouncy jumps that get smaller, with a squash on each landing.
            val decay = 1f - inMood / 1.6f
            val phase = sin(inMood * PI * 3.75).toFloat()
            lift += abs(phase) * 30f * decay
            tilt += sin(inMood * PI * 5).toFloat() * 5f * decay
            if (abs(phase) < 0.25f) squash *= 1f - 0.08f * decay
        } else if (mood == Mood.CHEER && inMood < 1.2f) {
            val decay = 1f - inMood / 1.2f
            lift += abs(sin(inMood * PI * 3.3)).toFloat() * 10f * decay
            tilt += sin(inMood * PI * 3.3).toFloat() * 3f * decay
        }
        return Triple(lift, tilt, squash)
    }

    /** Syllable-like rhythm with a slower envelope and short pauses, so it never looks looped. */
    private fun speechOpenness(t: Double): Float {
        val syllables = 0.5 + 0.5 * sin(t * 2 * PI * 3.4)
        val phrase = 0.7 + 0.3 * sin(t * 2 * PI * 0.9 + 0.7)
        val pause = if (sin(t * 2 * PI * 0.41) > 0.9) 0.2 else 1.0
        return (syllables * phrase * pause).toFloat().coerceIn(0f, 1f)
    }

    private fun ease(current: Float, target: Float, rate: Float, dt: Float): Float =
        current + (target - current) * (1f - exp(-dt * rate))

    private fun scheduleBlink() {
        removeCallbacks(blinkRunnable)
        if (isAttachedToWindow) postDelayed(blinkRunnable, Random.nextLong(2600, 4800))
    }

    private fun blinkAmount(): Float {
        if (blinkStartMs < 0) return 0f
        val p = ((SystemClock.uptimeMillis() - blinkStartMs) / BLINK_MS.toFloat())
        if (p >= 1f) {
            blinkStartMs = -1L
            return 0f
        }
        return sin(p * PI).toFloat()
    }

    // ───────────────────────────── Drawing ─────────────────────────────

    private fun drawShadow(canvas: Canvas, lift: Float) {
        val shrink = (1f - lift / 80f).coerceIn(0.6f, 1f)
        fill.shader = null
        fill.color = Color.argb((34 * shrink).toInt(), 60, 20, 10)
        rect.set(CENTER_X - 74f * shrink, 266f, CENTER_X + 74f * shrink, 280f)
        canvas.drawOval(rect, fill)
    }

    private fun drawFeet(canvas: Canvas) {
        fill.color = SHOE
        oval(canvas, 92f, 260f, 20f, 10f)
        oval(canvas, 146f, 260f, 20f, 10f)
        fill.color = SHOE_SOLE
        rect.set(72f, 262f, 112f, 270f)
        canvas.drawRoundRect(rect, 5f, 5f, fill)
        rect.set(126f, 262f, 166f, 270f)
        canvas.drawRoundRect(rect, 5f, 5f, fill)
    }

    private fun drawBook(canvas: Canvas) {
        fill.shader = null

        // Back cover and the stack of pages peeking out on the right
        fill.color = BACK_COVER
        rect.set(56f, 62f, 204f, 254f)
        canvas.drawRoundRect(rect, 18f, 18f, fill)
        fill.color = PAGES
        rect.set(62f, 68f, 199f, 248f)
        canvas.drawRoundRect(rect, 12f, 12f, fill)
        stroke.color = PAGE_LINE
        stroke.strokeWidth = 2.5f
        canvas.drawLine(195f, 78f, 195f, 238f, stroke)
        canvas.drawLine(189f, 76f, 189f, 240f, stroke)

        // Front cover
        fill.shader = coverGradient
        rect.set(44f, 54f, 190f, 250f)
        canvas.drawRoundRect(rect, 22f, 22f, fill)
        fill.shader = null

        // Spine with gold bands
        fill.color = SPINE
        path.reset()
        path.moveTo(66f, 54f)
        path.lineTo(66f, 250f)
        path.arcTo(spineBottomArc, 90f, 90f)
        path.lineTo(44f, 76f)
        path.arcTo(spineTopArc, 180f, 90f)
        path.close()
        canvas.drawPath(path, fill)
        fill.color = GOLD
        rect.set(46f, 78f, 66f, 85f)
        canvas.drawRect(rect, fill)
        rect.set(46f, 218f, 66f, 225f)
        canvas.drawRect(rect, fill)

        // Gloss
        stroke.color = Color.argb(80, 255, 255, 255)
        stroke.strokeWidth = 6f
        path.reset()
        path.moveTo(148f, 66f)
        path.quadTo(176f, 70f, 178f, 98f)
        canvas.drawPath(path, stroke)

        // Gold star emblem in the corner
        fill.color = GOLD
        star(canvas, 168f, 228f, 10f, 4.5f, 0f)

        // Teal bookmark ribbon
        fill.color = RIBBON
        path.reset()
        path.moveTo(140f, 248f)
        path.lineTo(140f, 274f)
        path.lineTo(147f, 267f)
        path.lineTo(154f, 274f)
        path.lineTo(154f, 248f)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun drawHeadphones(canvas: Canvas) {
        val pulse = 1f + 0.08f * listenAmount + 0.22f * voiceLevel

        // Band over the top of the book
        stroke.color = BAND
        stroke.strokeWidth = 11f
        path.reset()
        path.moveTo(36f, 112f)
        path.cubicTo(30f, 2f - 6f * listenAmount, 206f, 2f - 6f * listenAmount, 200f, 112f)
        canvas.drawPath(path, stroke)
        stroke.color = BAND_HIGHLIGHT
        stroke.strokeWidth = 3f
        path.reset()
        path.moveTo(52f, 60f)
        path.cubicTo(70f, 30f, 120f, 22f, 150f, 30f)
        canvas.drawPath(path, stroke)

        drawEarCup(canvas, 36f, pulse)
        drawEarCup(canvas, 200f, pulse)
    }

    private fun drawEarCup(canvas: Canvas, cx: Float, pulse: Float) {
        val cy = 132f
        canvas.save()
        canvas.scale(pulse, pulse, cx, cy)
        if (listenAmount > 0.02f) {
            fill.color = Color.argb((90 * listenAmount * (0.4f + voiceLevel)).toInt().coerceAtMost(160), 34, 179, 166)
            oval(canvas, cx, cy, 24f, 32f)
        }
        fill.color = CUP
        rect.set(cx - 14f, cy - 24f, cx + 14f, cy + 24f)
        canvas.drawRoundRect(rect, 12f, 12f, fill)
        fill.color = CUP_LIGHT
        rect.set(cx - 8f, cy - 17f, cx + 2f, cy + 17f)
        canvas.drawRoundRect(rect, 6f, 6f, fill)
        canvas.restore()
    }

    private fun drawSoundWaves(canvas: Canvas) {
        if (listenAmount < 0.02f) return
        stroke.strokeWidth = 4f
        for (i in 0..2) {
            val wavePhase = ((clockSec * 1.4 + i / 3.0) % 1.0).toFloat()
            val radius = 14f + wavePhase * 26f
            val alpha = (200 * listenAmount * (1f - wavePhase) * (0.35f + voiceLevel)).toInt().coerceIn(0, 230)
            stroke.color = Color.argb(alpha, 34, 179, 166)
            rect.set(200f - radius, 132f - radius, 200f + radius, 132f + radius)
            canvas.drawArc(rect, -45f, 90f, false, stroke)
            rect.set(36f - radius, 132f - radius, 36f + radius, 132f + radius)
            canvas.drawArc(rect, 135f, 90f, false, stroke)
        }
    }

    private fun drawFace(canvas: Canvas) {
        // Brows
        stroke.color = INK
        stroke.strokeWidth = 4f
        path.reset()
        path.moveTo(84f, 108f - browLeft)
        path.quadTo(96f, 100f - browLeft * 1.4f, 108f, 106f - browLeft)
        path.moveTo(128f, 106f - browRight)
        path.quadTo(140f, 100f - browRight * 1.4f, 152f, 108f - browRight)
        canvas.drawPath(path, stroke)

        val blink = blinkAmount()
        drawEye(canvas, 96f, blink)
        drawEye(canvas, 140f, blink)

        fill.color = CHEEK
        oval(canvas, 80f, 154f, 11f, 7f)
        oval(canvas, 156f, 154f, 11f, 7f)

        drawMouth(canvas)
    }

    private fun drawEye(canvas: Canvas, cx: Float, blink: Float) {
        val cy = 130f
        if (happyEyes > 0.5f) {
            // Joyful closed "^" eyes
            stroke.color = INK
            stroke.strokeWidth = 4.5f
            path.reset()
            path.moveTo(cx - 11f, cy + 3f)
            path.quadTo(cx, cy - 12f * happyEyes, cx + 11f, cy + 3f)
            canvas.drawPath(path, stroke)
            return
        }
        val squash = (1f - blink) * (1f - happyEyes * 0.8f)
        if (squash < 0.2f) {
            stroke.color = INK
            stroke.strokeWidth = 4f
            path.reset()
            path.moveTo(cx - 11f, cy)
            path.quadTo(cx, cy + 6f, cx + 11f, cy)
            canvas.drawPath(path, stroke)
            return
        }
        canvas.save()
        canvas.scale(1f, squash, cx, cy)
        fill.color = INK
        oval(canvas, cx + lookX, cy + lookY, 11f, 14f)
        fill.color = Color.WHITE
        canvas.drawCircle(cx + lookX - 4f, cy + lookY - 5f, 4.2f, fill)
        canvas.drawCircle(cx + lookX + 4f, cy + lookY + 5f, 1.8f, fill)
        canvas.restore()
    }

    private fun drawMouth(canvas: Canvas) {
        val cx = 118f
        val cy = 166f

        when {
            smileAmount > 0.02f -> {
                // Big open grin for celebrating and cheering
                val half = 12f + 11f * smileAmount
                val drop = 4f + 22f * smileAmount
                path.reset()
                path.moveTo(cx - half, cy - 4f)
                path.quadTo(cx, cy - 1f, cx + half, cy - 4f)
                path.quadTo(cx, cy + drop * 1.6f, cx - half, cy - 4f)
                path.close()
                fill.color = MOUTH
                canvas.drawPath(path, fill)
                canvas.save()
                canvas.clipPath(path)
                fill.color = Color.WHITE
                rect.set(cx - half, cy - 6f, cx + half, cy + 1f + 3f * smileAmount)
                canvas.drawRect(rect, fill)
                fill.color = TONGUE
                oval(canvas, cx, cy + drop * 0.9f, 9f + 4f * smileAmount, 7f * smileAmount + 2f)
                canvas.restore()
                stroke.color = INK
                stroke.strokeWidth = 4f
                canvas.drawPath(path, stroke)
            }
            mouthOpen > 0.03f -> {
                // Rounded "O" mouth: narrows a little and drops open as it speaks
                val rx = 13f - 3f * mouthOpen
                val ry = 3f + 13f * mouthOpen
                rect.set(cx - rx, cy - ry * 0.45f, cx + rx, cy + ry * 1.1f)
                fill.color = MOUTH
                canvas.drawOval(rect, fill)
                canvas.save()
                path.reset()
                path.addOval(rect, Path.Direction.CW)
                canvas.clipPath(path)
                fill.color = TONGUE
                oval(canvas, cx, rect.bottom - ry * 0.25f, rx * 0.7f, ry * 0.45f)
                canvas.restore()
                stroke.color = INK
                stroke.strokeWidth = 4f
                canvas.drawOval(rect, stroke)
            }
            else -> {
                // Resting smile
                stroke.color = INK
                stroke.strokeWidth = 4f
                path.reset()
                path.moveTo(cx - 13f, cy - 2f)
                path.quadTo(cx, cy + 10f, cx + 13f, cy - 2f)
                canvas.drawPath(path, stroke)
            }
        }
    }

    private fun drawArms(canvas: Canvas) {
        drawArm(canvas, 50f, 196f, leftHand, -1f)
        drawArm(canvas, 186f, 196f, rightHand, 1f)

        if (thumbAmount > 0.05f) {
            // Thumbs-up on the right hand
            canvas.save()
            canvas.scale(thumbAmount, thumbAmount, rightHand.x, rightHand.y)
            fill.color = HAND
            rect.set(rightHand.x - 4.5f, rightHand.y - 24f, rightHand.x + 4.5f, rightHand.y - 4f)
            canvas.drawRoundRect(rect, 4.5f, 4.5f, fill)
            stroke.color = INK
            stroke.strokeWidth = 2.5f
            canvas.drawRoundRect(rect, 4.5f, 4.5f, stroke)
            canvas.restore()
        }

        fill.color = HAND
        canvas.drawCircle(leftHand.x, leftHand.y, 11f, fill)
        canvas.drawCircle(rightHand.x, rightHand.y, 11f, fill)
        stroke.color = INK
        stroke.strokeWidth = 2.5f
        canvas.drawCircle(leftHand.x, leftHand.y, 11f, stroke)
        canvas.drawCircle(rightHand.x, rightHand.y, 11f, stroke)
    }

    private fun drawArm(canvas: Canvas, shoulderX: Float, shoulderY: Float, hand: PointF, side: Float) {
        stroke.color = ARM
        stroke.strokeWidth = 9f
        path.reset()
        path.moveTo(shoulderX, shoulderY)
        val midX = (shoulderX + hand.x) / 2f + side * 10f
        val midY = max(shoulderY, hand.y) + 6f
        path.quadTo(midX, midY, hand.x, hand.y)
        canvas.drawPath(path, stroke)
    }

    private fun drawSparkles(canvas: Canvas) {
        if (smileAmount < 0.05f || mood != Mood.HAPPY) return
        SPARKLES.forEachIndexed { i, (x, y) ->
            val twinkle = (0.5f + 0.5f * sin(clockSec * 2 * PI * 1.8 + i * 1.3).toFloat()) * smileAmount
            fill.color = if (i % 2 == 0) GOLD else SPARKLE_PINK
            star(canvas, x, y, 9f * twinkle + 2f, 3.5f * twinkle + 1f, (clockSec * 60 + i * 20).toFloat())
        }
    }

    private fun drawThinkingDots(canvas: Canvas) {
        if (thinkAmount < 0.05f) return
        for (i in 0..2) {
            val bounce = abs(sin(clockSec * 2 * PI * 1.2 - i * 0.5)).toFloat() * 6f
            fill.color = Color.argb((220 * thinkAmount).toInt(), 59, 53, 97)
            canvas.drawCircle(198f + i * 14f, 34f - i * 8f - bounce, 4.5f + i * 0.8f, fill)
        }
    }

    private fun star(canvas: Canvas, cx: Float, cy: Float, outer: Float, inner: Float, rotationDeg: Float) {
        path.reset()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(rotationDeg - 90.0 + i * 36.0)
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun oval(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawOval(rect, fill)
    }

    private companion object {
        const val DESIGN_W = 240f
        const val DESIGN_H = 282f
        const val CENTER_X = 118f
        const val GROUND_Y = 270f
        const val BLINK_MS = 150L

        val COVER_TOP = Color.parseColor("#FF8A6B")
        val COVER_BOTTOM = Color.parseColor("#F0604E")
        val BACK_COVER = Color.parseColor("#C9473B")
        val SPINE = Color.parseColor("#DD5445")
        val PAGES = Color.parseColor("#FFF4DE")
        val PAGE_LINE = Color.parseColor("#EBD9B4")
        val GOLD = Color.parseColor("#FFC93C")
        val RIBBON = Color.parseColor("#22B3A6")
        val BAND = Color.parseColor("#3B3561")
        val BAND_HIGHLIGHT = Color.parseColor("#6E66A8")
        val CUP = Color.parseColor("#22B3A6")
        val CUP_LIGHT = Color.parseColor("#7FE0D6")
        val INK = Color.parseColor("#3B2440")
        val MOUTH = Color.parseColor("#7E2030")
        val TONGUE = Color.parseColor("#FF7A90")
        val CHEEK = Color.parseColor("#E6FFB3C1")
        val ARM = Color.parseColor("#E0503F")
        val HAND = Color.parseColor("#FF9B80")
        val SHOE = Color.parseColor("#22B3A6")
        val SHOE_SOLE = Color.parseColor("#FFFFFF")
        val SPARKLE_PINK = Color.parseColor("#FF7AB6")

        val SPARKLES = listOf(
            18f to 60f, 222f to 52f, 10f to 170f, 230f to 196f, 60f to 20f, 186f to 18f
        )
    }
}
