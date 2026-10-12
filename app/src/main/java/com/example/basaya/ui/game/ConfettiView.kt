package com.example.basaya.ui.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Lightweight confetti overlay. Never intercepts touches. */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var rotation: Float,
        val spin: Float,
        val size: Float,
        val color: Int,
        val round: Boolean,
        var life: Float
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var lastFrameNanos = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Small burst radiating from a point (e.g. when a word is found). */
    fun burst(x: Float, y: Float, count: Int = 26) {
        repeat(count) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val speed = (220 + Random.nextFloat() * 380) * density
            particles += newParticle(
                x, y,
                (cos(angle) * speed).toFloat(),
                (sin(angle) * speed).toFloat() - 180 * density
            )
        }
        start()
    }

    /** Full-screen shower from the top (level complete). */
    fun rain(count: Int = 120) {
        repeat(count) {
            particles += newParticle(
                Random.nextFloat() * width,
                -Random.nextFloat() * height * 0.4f,
                (Random.nextFloat() - 0.5f) * 160 * density,
                (80 + Random.nextFloat() * 220) * density
            ).apply { life = 3.2f }
        }
        start()
    }

    private fun newParticle(x: Float, y: Float, vx: Float, vy: Float) = Particle(
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        rotation = Random.nextFloat() * 360f,
        spin = (Random.nextFloat() - 0.5f) * 720f,
        size = (6 + Random.nextFloat() * 6) * density,
        color = CandyTile.PALETTE[Random.nextInt(CandyTile.PALETTE.size)].fill,
        round = Random.nextBoolean(),
        life = 1.6f
    )

    private fun start() {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = System.nanoTime()
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (particles.isEmpty()) {
            lastFrameNanos = 0L
            return
        }

        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.05f)
        lastFrameNanos = now
        val gravity = 900 * density

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.vy += gravity * dt
            p.vx *= 0.99f
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.spin * dt
            p.life -= dt
            if (p.life <= 0f || p.y > height + p.size * 4) {
                iterator.remove()
                continue
            }

            paint.color = p.color
            paint.alpha = (255 * p.life.coerceIn(0f, 0.5f) * 2).toInt()
            canvas.save()
            canvas.rotate(p.rotation, p.x, p.y)
            if (p.round) {
                canvas.drawCircle(p.x, p.y, p.size / 2f, paint)
            } else {
                canvas.drawRect(p.x - p.size / 2f, p.y - p.size / 4f, p.x + p.size / 2f, p.y + p.size / 4f, paint)
            }
            canvas.restore()
        }

        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        particles.clear()
        lastFrameNanos = 0L
        super.onDetachedFromWindow()
    }
}
