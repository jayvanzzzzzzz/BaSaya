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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ph.basaya.app.R
import ph.basaya.app.data.notifications.LessonInbox
import ph.basaya.app.data.schedule.TrustedClock
import ph.basaya.app.ui.MainActivity
import ph.basaya.app.ui.pronunciation.PronunciationBookView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)

        val header = findViewById<View>(R.id.header)
        recycler = findViewById(R.id.recyclerNotifications)
        val listBottom = recycler.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            recycler.updatePadding(bottom = listBottom + bars.bottom)
            insets
        }
        // White status bar icons on the violet header
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        readBefore = intent.getLongExtra(EXTRA_READ_BEFORE, 0L)
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
        val items = LessonInbox.visible(this, uid)
        val (unread, earlier) = items.partition { it.time > readBefore }

        val rows = buildList {
            if (unread.isNotEmpty()) {
                add(NotificationAdapter.Row.Section(getString(R.string.notif_section_new)))
                unread.forEach { add(NotificationAdapter.Row.Entry(it, unread = true)) }
            }
            if (earlier.isNotEmpty()) {
                add(NotificationAdapter.Row.Section(getString(R.string.notif_section_earlier)))
                earlier.forEach { add(NotificationAdapter.Row.Entry(it, unread = false)) }
            }
        }
        adapter.submit(rows, TrustedClock.bestGuess(this))

        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        tvBubble.text = when {
            unread.isNotEmpty() ->
                resources.getQuantityString(R.plurals.notif_screen_bubble_unread, unread.size, unread.size)
            items.isNotEmpty() -> getString(R.string.notif_screen_bubble_none)
            else -> getString(R.string.notif_screen_bubble_empty)
        }
        buddy.setMood(
            if (unread.isNotEmpty()) PronunciationBookView.Mood.CHEER else PronunciationBookView.Mood.HAPPY
        )
    }
}
