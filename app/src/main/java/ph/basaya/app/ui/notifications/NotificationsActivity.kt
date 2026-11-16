package ph.basaya.app.ui.notifications

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ph.basaya.app.R
import ph.basaya.app.data.notifications.LessonInbox
import ph.basaya.app.data.schedule.TrustedClock
import ph.basaya.app.ui.MainActivity
import ph.basaya.app.ui.pronunciation.PronunciationBookView
import ph.basaya.app.ui.feedback.WordInboxMotion
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * The notification list: "new lesson" items and deadline reminders, newest first, with
 * the ones that arrived since the last visit under "BAGO". Tapping one goes to Home and
 * highlights its lesson.
 */
class NotificationsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_READ_BEFORE = "readBefore"

        /** [readBefore]: the "read up to" time before the bell marked everything as read. */
        fun intent(context: Context, readBefore: Long): Intent =
            Intent(context, NotificationsActivity::class.java).putExtra(EXTRA_READ_BEFORE, readBefore)
    }

    private lateinit var adapter: NotificationAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvBubble: TextView
    private lateinit var buddy: PronunciationBookView
    private var readBefore = 0L
    private var onlyNew = false
    private var items: List<LessonInbox.Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.tvScreenTitle), true)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.tvHeroTitle), true)
        if (resources.configuration.fontScale >= 1.3f) findViewById<View>(R.id.heroArtwork).visibility = View.GONE

        val header = findViewById<View>(R.id.header)
        val statusStrip = findViewById<View>(R.id.statusStrip)
        val body = findViewById<View>(R.id.inboxBody)
        recycler = findViewById(R.id.recyclerNotifications)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            header.minimumHeight = bars.top
            statusStrip.updateLayoutParams { height = bars.top }
            body.updatePadding(bottom = bars.bottom)
            insets
        }
        findViewById<AppBarLayout>(R.id.appBar).addOnOffsetChangedListener { _, offset ->
            statusStrip.alpha = if (statusStrip.height == 0) 0f else
                (-offset / statusStrip.height.toFloat()).coerceIn(0f, 1f)
        }
        // White status bar icons on the violet header
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        readBefore = intent.getLongExtra(EXTRA_READ_BEFORE, 0L)
        onlyNew = savedInstanceState?.getBoolean("onlyNew") ?: false
        emptyState = findViewById(R.id.emptyState)
        tvBubble = findViewById(R.id.tvBubble)
        buddy = findViewById(R.id.buddy)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        adapter = NotificationAdapter { item ->
            startActivity(MainActivity.highlightIntent(this, item.lessonIds))
            finish()
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        findViewById<View>(R.id.filterAll).setOnClickListener { selectFilter(false) }
        findViewById<View>(R.id.filterNew).setOnClickListener { selectFilter(true) }
        render()

        // Reload when a push arrives or a reminder comes due while this screen is open.
        lifecycleScope.launch {
            LessonInbox.changes.collect {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) load()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        items = LessonInbox.visible(this, uid)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("onlyNew", onlyNew)
        super.onSaveInstanceState(outState)
    }

    private fun selectFilter(newOnly: Boolean) {
        if (onlyNew == newOnly) return
        onlyNew = newOnly
        render()
        recycler.scrollToPosition(0)
        WordInboxMotion.reveal(if (recycler.visibility == View.VISIBLE) recycler else emptyState)
    }

    private fun render() {
        val (unread, earlier) = items.partition { it.time > readBefore }

        val rows = buildList {
            if (unread.isNotEmpty()) {
                add(NotificationAdapter.Row.Section(getString(R.string.notif_section_new)))
                unread.forEach { add(NotificationAdapter.Row.Entry(it, unread = true)) }
            }
            if (!onlyNew && earlier.isNotEmpty()) {
                add(NotificationAdapter.Row.Section(getString(R.string.notif_section_earlier)))
                earlier.forEach { add(NotificationAdapter.Row.Entry(it, unread = false)) }
            }
        }
        adapter.submit(rows, TrustedClock.bestGuess(this))

        val empty = if (onlyNew) unread.isEmpty() else items.isEmpty()
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvEmptyTitle).setText(
            if (onlyNew && items.isNotEmpty()) R.string.wi_no_new_title else R.string.notif_empty_title
        )
        findViewById<TextView>(R.id.tvEmptyBody).setText(
            if (onlyNew && items.isNotEmpty()) R.string.wi_no_new_body else R.string.notif_empty_body
        )
        listOf(R.id.filterAll to !onlyNew, R.id.filterNew to onlyNew).forEach { (id, selected) ->
            findViewById<TextView>(id).apply {
                isSelected = selected
                setBackgroundResource(if (selected) R.drawable.wi_tab_selected else R.drawable.wi_tab)
                setTextColor(ContextCompat.getColor(context, if (selected) R.color.wi_on_action else R.color.wi_action))
            }
        }
        val summary = when {
            unread.isNotEmpty() ->
                resources.getQuantityString(R.plurals.notif_screen_bubble_unread, unread.size, unread.size)
            items.isNotEmpty() -> getString(R.string.wi_notif_summary_read)
            else -> getString(R.string.notif_screen_bubble_empty)
        }
        if (tvBubble.text.toString() != summary) {
            tvBubble.text = summary
            WordInboxMotion.reveal(tvBubble)
        }
        // A reminder, including a closed lesson, never triggers a celebration.
        buddy.setMood(PronunciationBookView.Mood.IDLE)
    }
}
