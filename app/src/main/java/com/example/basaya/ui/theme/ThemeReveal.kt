package com.example.basaya.ui.theme

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnPreDraw
import androidx.core.view.drawToBitmap
import kotlin.math.hypot
import kotlin.math.max

/**
 * Circle-reveal theme switch: the new theme grows out of the tapped button.
 *
 * Switching light/dark recreates the screen, so before that we take a picture of the old one.
 * The recreated screen then shows that picture on top and cuts a growing hole in it.
 */
object ThemeReveal {

    private const val DURATION_MS = 600L
    // A picture older than this belongs to a switch that never recreated the screen.
    private const val MAX_AGE_MS = 3_000L
    // Gives restored text and lists a frame to settle before the circle starts.
    private const val START_DELAY_MS = 60L

    private var snapshot: Bitmap? = null
    private var centerX = 0f
    private var centerY = 0f
    private var takenAt = 0L

    /** Picks [mode]; animates from [from] when the colors actually change. */
    fun switchTo(activity: Activity, mode: ThemeMode, from: View) {
        if (activity.isNightTheme() == willBeNight(mode)) {
            ThemeMode.choose(activity, mode) // same colors: nothing is recreated, nothing to animate
            return
        }
        capture(activity, from) { ThemeMode.choose(activity, mode) }
    }

    /** Call right after setContentView() on the screen that hosts the theme picker. */
    fun playIfPending(activity: Activity) {
        val picture = snapshot ?: return
        snapshot = null
        if (SystemClock.uptimeMillis() - takenAt > MAX_AGE_MS) {
            picture.recycle()
            return
        }

        val root = activity.window.decorView as ViewGroup
        val overlay = RevealOverlay(activity, picture, centerX, centerY)
        root.addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        root.doOnPreDraw {
            val endRadius = max(
                max(hypot(centerX, centerY), hypot(root.width - centerX, centerY)),
                max(hypot(centerX, root.height - centerY), hypot(root.width - centerX, root.height - centerY))
            )
            ValueAnimator.ofFloat(0f, endRadius).apply {
                duration = DURATION_MS
                startDelay = START_DELAY_MS
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addUpdateListener { overlay.radius = it.animatedValue as Float }
                doOnEnd {
                    root.removeView(overlay)
                    picture.recycle()
                }
                start()
            }
        }
    }

    /** Takes the picture, then runs [then] (which switches the theme) either way. */
    private fun capture(activity: Activity, from: View, then: () -> Unit) {
        val decor = activity.window.decorView
        if (!decor.isLaidOut || decor.width == 0 || decor.height == 0) return then()

        val inWindow = IntArray(2)
        from.getLocationInWindow(inWindow)
        val x = inWindow[0] + from.width / 2f
        val y = inWindow[1] + from.height / 2f

        fun keep(picture: Bitmap?) {
            snapshot?.recycle()
            snapshot = picture
            centerX = x
            centerY = y
            takenAt = SystemClock.uptimeMillis()
            then()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Copies the real screen pixels, shadows included.
            val picture = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            val result = runCatching {
                PixelCopy.request(activity.window, picture, { code ->
                    if (code == PixelCopy.SUCCESS) {
                        keep(picture)
                    } else {
                        picture.recycle()
                        keep(null)
                    }
                }, Handler(Looper.getMainLooper()))
            }
            if (result.isFailure) {
                picture.recycle()
                keep(runCatching { decor.drawToBitmap() }.getOrNull())
            }
        } else {
            keep(runCatching { decor.drawToBitmap() }.getOrNull())
        }
    }

    private fun willBeNight(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> Resources.getSystem().configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /** The old screen with a transparent hole that grows; swallows taps while it plays. */
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private class RevealOverlay(
        context: Context,
        private val picture: Bitmap,
        private val cx: Float,
        private val cy: Float
    ) : View(context) {

        private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        var radius = 0f
            set(value) {
                field = value
                invalidate()
            }

        init {
            setOnTouchListener { _, _ -> true }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        override fun onDraw(canvas: Canvas) {
            val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawBitmap(picture, 0f, 0f, null)
            if (radius > 0f) canvas.drawCircle(cx, cy, radius, holePaint)
            canvas.restoreToCount(layer)
        }
    }
}
