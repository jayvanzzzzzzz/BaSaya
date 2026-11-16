package ph.basaya.app.ui.notifications

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import ph.basaya.app.R
import ph.basaya.app.data.notifications.LessonInbox
import ph.basaya.app.data.notifications.LessonInbox.Kind
import ph.basaya.app.ui.feedback.WordInboxMotion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val onClick: (LessonInbox.Item) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Section(val label: String) : Row()
        data class Entry(val item: LessonInbox.Item, val unread: Boolean) : Row()
    }

    private var rows: List<Row> = emptyList()
    private var now: Long = 0L
    private val revealed = mutableSetOf<Int>()

    fun submit(newRows: List<Row>, nowMillis: Long) {
        rows = newRows
        now = nowMillis
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) =
        if (rows[position] is Row.Section) TYPE_SECTION else TYPE_ENTRY

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SECTION) {
            SectionHolder(inflater.inflate(R.layout.item_notification_section, parent, false))
        } else {
            EntryHolder(inflater.inflate(R.layout.item_notification, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
        when (val row = rows[position]) {
            is Row.Section -> (holder as SectionHolder).label.text = row.label
            is Row.Entry -> (holder as EntryHolder).bind(row)
        }
        if (position < FIRST_SCREEN && revealed.add(position)) slideIn(holder.itemView, position)
    }

    private fun slideIn(view: View, position: Int) {
        if (position >= FIRST_SCREEN || !WordInboxMotion.enabled(view)) return
        val d = view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = 18 * d
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay(minOf(position, 3) * 35L).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private class SectionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById<TextView>(R.id.tvSection).also {
            ViewCompat.setAccessibilityHeading(it, true)
        }
    }

    private inner class EntryHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconTile: View = view.findViewById(R.id.iconTile)
        private val ivKind: ImageView = view.findViewById(R.id.ivKind)
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvBody: TextView = view.findViewById(R.id.tvBody)
        private val tvDay: TextView = view.findViewById(R.id.tvDay)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val unreadDot: View = view.findViewById(R.id.unreadDot)
        private val tvKind: TextView = view.findViewById(R.id.tvKind)

        fun bind(row: Row.Entry) {
            val context = itemView.context
            val item = row.item
            val style = styleFor(item.kind)
            val accent = ContextCompat.getColor(context, style.accent)

            iconTile.backgroundTintList = ContextCompat.getColorStateList(context, style.tint)
            ivKind.setImageResource(style.icon)
            ivKind.imageTintList = ColorStateList.valueOf(accent)
            tvKind.setText(style.label)
            tvKind.setTextColor(accent)
            // The icon and kind label replace decorative emoji in notification payloads.
            tvTitle.text = item.title.replace(Regex("^[\\p{So}\\p{Sk}\\uFE0F\\u200D\\s]+"), "")
            tvBody.text = item.body

            val (day, time) = whenLabel(context, item.time, now)
            tvDay.text = day
            tvDay.setTextColor(accent)
            tvTime.text = time

            itemView.setBackgroundResource(
                if (row.unread) R.drawable.wi_notification_new else R.drawable.wi_notification_read
            )
            unreadDot.visibility = if (row.unread) View.VISIBLE else View.GONE
            val description = "${context.getString(style.label)}. ${tvTitle.text}. ${item.body}. $day $time"
            itemView.contentDescription = if (row.unread) context.getString(R.string.wi_unread_cd, description) else description
            itemView.setOnClickListener { onClick(item) }
        }
    }

    private data class KindStyle(val icon: Int, val accent: Int, val tint: Int, val label: Int)

    private fun styleFor(kind: Kind) = when (kind) {
        Kind.NEW_LESSON -> KindStyle(R.drawable.ic_book, R.color.wi_action, R.color.wi_violet_soft, R.string.wi_kind_lesson)
        Kind.DEADLINE_DAY -> KindStyle(R.drawable.ic_alarm, R.color.wi_amber_ink, R.color.wi_amber_soft, R.string.wi_kind_day)
        Kind.DEADLINE_HOUR -> KindStyle(R.drawable.ic_alarm, R.color.wi_urgent, R.color.wi_urgent_soft, R.string.wi_kind_hour)
        Kind.CLOSED -> KindStyle(R.drawable.ic_lock_small, R.color.wi_muted, R.color.wi_violet_soft, R.string.wi_kind_closed)
    }

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_ENTRY = 1
        private const val FIRST_SCREEN = 10

        private val filipino: Locale = Locale.forLanguageTag("fil-PH")

        /** When it arrived, as (day, clock): ("5 min", "3:15 PM"), ("Kahapon", "3:15 PM"), ("Okt 12", "3:15 PM"). */
        fun whenLabel(context: Context, time: Long, now: Long): Pair<String, String> {
            val clock = SimpleDateFormat("h:mm a", filipino).format(Date(time))
            val minutes = (now - time) / 60_000L
            val day = when {
                minutes < 1 -> context.getString(R.string.notif_time_just_now)
                minutes < 60 -> context.getString(R.string.notif_time_minutes, minutes)
                sameDay(time, now) -> context.getString(R.string.notif_time_today)
                sameDay(time, now - 24 * 60 * 60 * 1000L) -> context.getString(R.string.notif_time_yesterday)
                else -> SimpleDateFormat(
                    if (sameYear(time, now)) "MMM d" else "MMM d, yyyy", filipino
                ).format(Date(time))
            }
            return day to clock
        }

        private fun sameDay(a: Long, b: Long): Boolean {
            val x = Calendar.getInstance().apply { timeInMillis = a }
            val y = Calendar.getInstance().apply { timeInMillis = b }
            return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) &&
                x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
        }

        private fun sameYear(a: Long, b: Long): Boolean {
            val x = Calendar.getInstance().apply { timeInMillis = a }
            val y = Calendar.getInstance().apply { timeInMillis = b }
            return x.get(Calendar.YEAR) == y.get(Calendar.YEAR)
        }
    }
}
