package ph.basaya.app.ui

import android.animation.Animator
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import ph.basaya.app.R
import ph.basaya.app.data.repository.LessonPrefetch
import ph.basaya.app.ui.theme.isNightTheme
import ph.basaya.app.ui.theme.useDarkStatusBarIconsInLightTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Animated launch screen: the logo bounces in, "BaSaya" pops in as toy letter blocks, and stars and
 * bubbles drift around while the class lessons sync. Leaves once the sync is done (at least
 * [MIN_SHOW_MS], at most [MAX_WAIT_MS]) for Login or Main.
 */
class SplashActivity : AppCompatActivity() {

    private class Block(@ColorRes val face: Int, @ColorRes val lip: Int, @ColorRes val ink: Int)

    private val letters = mutableListOf<TextView>()
    private val loops = mutableListOf<Animator>()
    private var startedAt = 0L
    private var next: Class<out Activity>? = null
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        useDarkStatusBarIconsInLightTheme()

        // Kept across rotation, so turning the phone doesn't restart the minimum show time.
        startedAt = savedInstanceState?.getLong(STATE_STARTED_AT) ?: SystemClock.elapsedRealtime()

        buildLetterBlocks()
        playIntro()

        val user = FirebaseAuth.getInstance().currentUser
        val pendingGoogleProfileUid = getSharedPreferences(
            CompleteProfileActivity.PREFS_NAME,
            MODE_PRIVATE
        ).getString(CompleteProfileActivity.KEY_PENDING_GOOGLE_UID, null)
        val needsProfileCompletion = user != null && pendingGoogleProfileUid == user.uid
        val sync = user?.takeUnless { needsProfileCompletion }?.let {
            LessonPrefetch.start(this, it.uid)
        }

        lifecycleScope.launch {
            // Wait for the lessons, but never trap a child here on bad Wi-Fi: Home shows the
            // cached lessons and picks up the still-running sync.
            if (sync != null) withTimeoutOrNull(MAX_WAIT_MS - elapsed()) { sync.await() }
            delay(MIN_SHOW_MS - elapsed())
            next = when {
                user == null -> LogInActivity::class.java
                needsProfileCompletion -> CompleteProfileActivity::class.java
                else -> MainActivity::class.java
            }
            // If the app was sent to the background meanwhile, wait until it's visible again.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) leave()
        }
    }

    override fun onStart() {
        super.onStart()
        leave()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_STARTED_AT, startedAt)
    }

    override fun onDestroy() {
        loops.forEach { it.cancel() }
        super.onDestroy()
    }

    private fun elapsed() = SystemClock.elapsedRealtime() - startedAt

    private fun buildLetterBlocks() {
        val row = findViewById<LinearLayout>(R.id.splashLetters)
        val size = dp(46f).toInt()
        val lip = dp(5f).toInt()
        val font = ResourcesCompat.getFont(this, R.font.be_vietnam_bold)

        getString(R.string.app_title).forEachIndexed { i, letter ->
            val block = BLOCKS[i % BLOCKS.size]
            val view = TextView(this).apply {
                text = letter.toString()
                gravity = Gravity.CENTER
                typeface = font
                includeFontPadding = false
                // Fixed size so the letter always fits its block; the row reads "BaSaya" as a whole.
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26f)
                setTextColor(ContextCompat.getColor(context, block.ink))
                background = blockDrawable(block, lip)
                setPadding(0, 0, 0, lip)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(view, LinearLayout.LayoutParams(size, size + lip).apply {
                if (i > 0) marginStart = dp(5f).toInt()
            })
            letters += view
        }
    }

    /** A rounded toy block: the face sits on a darker lip so it looks chunky. */
    private fun blockDrawable(block: Block, lip: Int): Drawable {
        fun rounded(@ColorRes color: Int) = GradientDrawable().apply {
            cornerRadius = dp(12f)
            setColor(ContextCompat.getColor(this@SplashActivity, color))
        }
        return LayerDrawable(arrayOf(rounded(block.lip), rounded(block.face))).apply {
            setLayerInset(1, 0, 0, 0, lip)
        }
    }

    private fun playIntro() {
        val root = findViewById<ViewGroup>(R.id.splashContent).parent as ViewGroup
        val logo = findViewById<View>(R.id.splashLogoCard)
        val glow = findViewById<View>(R.id.splashGlow)
        val dots = findViewById<ViewGroup>(R.id.splashDots)

        // 1. The logo springs in with a little wobble, and its halo blooms behind it.
        logo.scaleX = 0f
        logo.scaleY = 0f
        logo.rotation = -18f
        logo.animate().scaleX(1f).scaleY(1f).rotation(0f)
            .setStartDelay(150).setDuration(650)
            .setInterpolator(OvershootInterpolator(2.2f))
            .withEndAction { loop(breathe(logo, 1.04f, 1600)) }
            .start()

        glow.alpha = 0f
        glow.scaleX = 0.6f
        glow.scaleY = 0.6f
        glow.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(300).setDuration(700)
            .withEndAction { loop(breathe(glow, 1.12f, 1600)) }
            .start()

        // 2. The letter blocks drop in one by one and land slightly tilted, like toys.
        letters.forEachIndexed { i, block ->
            val tilt = TILTS[i % TILTS.size]
            block.scaleX = 0f
            block.scaleY = 0f
            block.translationY = -dp(40f)
            block.rotation = tilt * -6
            block.animate().scaleX(1f).scaleY(1f).translationY(0f).rotation(tilt)
                .setStartDelay(LETTERS_START_MS + i * LETTER_STAGGER_MS).setDuration(420)
                .setInterpolator(OvershootInterpolator(3f))
                .start()
        }
        val lettersDone = LETTERS_START_MS + letters.size * LETTER_STAGGER_MS + 420

        // 3. While the lessons load, the blocks hop in a wave and the dots bounce.
        letters.forEachIndexed { i, block ->
            loop(hop(block, dp(10f), 1800, lettersDone + i * 90L))
        }
        dots.alpha = 0f
        dots.animate().alpha(1f).setStartDelay(lettersDone - 200).setDuration(300).start()
        dots.children.forEachIndexed { i, dot ->
            loop(hop(dot, dp(9f), 900, lettersDone + i * 150L))
        }

        // Background: bubbles float, stars spin and twinkle, the waves sway.
        var popDelay = 200L
        root.children.forEach { view ->
            when (view.tag) {
                "bubble" -> {
                    popIn(view, popDelay)
                    loop(drift(view, dp(14f), 2400L + popDelay * 3))
                }
                "star" -> {
                    popIn(view, popDelay)
                    loop(spin(view, 9000L + popDelay * 10))
                    loop(twinkle(view, 1100L + popDelay, popDelay + 500))
                }
                else -> return@forEach
            }
            popDelay += 120
        }
        loop(drift(findViewById(R.id.splashWave), dp(18f), 3800, View.TRANSLATION_X))

        // Night Sky only: the moon (and its twinkles, tagged as stars above) come out.
        if (isNightTheme()) {
            findViewById<Group>(R.id.splashNightScenery).isVisible = true
            val moon = findViewById<View>(R.id.splashMoon)
            popIn(moon, 100)
            loop(drift(moon, dp(8f), 3200))
        }
    }

    private fun leave() {
        val next = next ?: return
        if (leaving) return
        leaving = true
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this, android.R.anim.fade_in, android.R.anim.fade_out
        )
        // The logo and blocks puff up and fade, then the next screen fades in.
        findViewById<View>(R.id.splashContent).animate()
            .scaleX(1.12f).scaleY(1.12f).alpha(0f)
            .setDuration(260).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                startActivity(Intent(this, next), options.toBundle())
                finish()
            }
            .start()
    }

    private fun loop(animator: Animator) {
        loops += animator
        animator.start()
    }

    private fun popIn(view: View, delay: Long) {
        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay).setDuration(450)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    private fun breathe(view: View, scale: Float, duration: Long) = ObjectAnimator.ofPropertyValuesHolder(
        view,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, scale),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, scale),
    ).apply {
        this.duration = duration
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
    }

    /** A quick jump at the start of each cycle, then rest; [delay] staggers views into a wave. */
    private fun hop(view: View, height: Float, cycle: Long, delay: Long): Animator {
        val up = Keyframe.ofFloat(0.12f, -height).apply { interpolator = DecelerateInterpolator() }
        val down = Keyframe.ofFloat(0.26f, 0f).apply { interpolator = AccelerateInterpolator() }
        val path = PropertyValuesHolder.ofKeyframe(
            View.TRANSLATION_Y, Keyframe.ofFloat(0f, 0f), up, down, Keyframe.ofFloat(1f, 0f)
        )
        return ObjectAnimator.ofPropertyValuesHolder(view, path).apply {
            duration = cycle
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
        }
    }

    private fun drift(
        view: View,
        distance: Float,
        duration: Long,
        property: android.util.Property<View, Float> = View.TRANSLATION_Y,
    ) = ObjectAnimator.ofFloat(view, property, -distance / 2, distance / 2).apply {
        this.duration = duration
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
    }

    private fun spin(view: View, duration: Long) =
        ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

    private fun twinkle(view: View, duration: Long, delay: Long) = ObjectAnimator.ofPropertyValuesHolder(
        view,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.7f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.7f),
    ).apply {
        this.duration = duration
        // Only after the star has popped in, so the two don't fight over its scale.
        startDelay = delay
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val STATE_STARTED_AT = "splash_started_at"
        const val MIN_SHOW_MS = 2_000L
        const val MAX_WAIT_MS = 8_000L
        const val LETTERS_START_MS = 650L
        const val LETTER_STAGGER_MS = 110L

        // How much each block leans once it lands.
        val TILTS = floatArrayOf(-5f, 4f, -3f, 5f, -4f, 3f)

        val BLOCKS = listOf(
            Block(R.color.splash_block_coral, R.color.splash_block_coral_lip, R.color.splash_block_ink),
            Block(R.color.splash_block_tangerine, R.color.splash_block_tangerine_lip, R.color.splash_block_ink),
            Block(R.color.splash_block_gold, R.color.splash_block_gold_lip, R.color.splash_block_ink_on_gold),
            Block(R.color.splash_block_mint, R.color.splash_block_mint_lip, R.color.splash_block_ink),
            Block(R.color.splash_block_sky, R.color.splash_block_sky_lip, R.color.splash_block_ink),
            Block(R.color.splash_block_violet, R.color.splash_block_violet_lip, R.color.splash_block_ink),
        )
    }
}
