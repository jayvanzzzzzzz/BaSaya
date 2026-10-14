package com.example.basaya.adapter

import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.basaya.R
import com.example.basaya.data.chat.ClassChat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Class chat rows: "load older", day separators, and messages. Messages from the same
 * person within [GROUP_GAP_MS] are grouped: the name shows on the first, the avatar and
 * time on the last, and the bubbles sit closer together.
 */
class ChatAdapter(
    private val myUid: String,
    private val onLoadOlder: () -> Unit,
    private val onLongPressMine: (ClassChat.Message) -> Unit
) : ListAdapter<ChatAdapter.Row, RecyclerView.ViewHolder>(Diff) {

    sealed class Row(val key: String) {
        data class Older(val loading: Boolean) : Row("older")
        data class Day(val label: String, val dayKey: Long) : Row("day:$dayKey")
        data class Msg(
            val message: ClassChat.Message,
            val first: Boolean,
            val last: Boolean
        ) : Row("msg:${message.id}")
    }

    companion object {
        private const val GROUP_GAP_MS = 5 * 60_000L
        private const val TYPE_OLDER = 0
        private const val TYPE_DAY = 1
        private const val TYPE_IN = 2
        private const val TYPE_MINE = 3
        private const val TYPE_REMOVED = 4

        // Classmates' avatars: one steady color per person.
        // Also used by the class members sheet (ChatFragment), so each person keeps their color.
        val AVATAR_COLORS = intArrayOf(
            0xFF4F8BFF.toInt(), 0xFF24994A.toInt(), 0xFFF2784B.toInt(), 0xFFE0509A.toInt(),
            0xFF1BA3A3.toInt(), 0xFFD9A21B.toInt(), 0xFF5C6BC0.toInt(), 0xFFB5651D.toInt()
        )
    }

    private object Diff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(old: Row, new: Row) = old.key == new.key
        override fun areContentsTheSame(old: Row, new: Row): Boolean {
            if (old is Row.Msg && new is Row.Msg) {
                val a = old.message
                val b = new.message
                return old.first == new.first && old.last == new.last && a.text == b.text &&
                    a.deleted == b.deleted && a.pending == b.pending && a.sentAt == b.sentAt
            }
            return old == new
        }
    }

    /** Rebuilds the rows from [messages] (oldest first). */
    fun show(messages: List<ClassChat.Message>, hasOlder: Boolean, loadingOlder: Boolean, context: android.content.Context) {
        val rows = ArrayList<Row>(messages.size + 8)
        if (hasOlder) rows += Row.Older(loadingOlder)
        var lastDay = -1L
        messages.forEachIndexed { i, m ->
            val day = dayKey(m.sentAt)
            if (day != lastDay) {
                rows += Row.Day(dayLabel(context, m.sentAt), day)
                lastDay = day
            }
            val prev = messages.getOrNull(i - 1)
            val next = messages.getOrNull(i + 1)
            rows += Row.Msg(m, first = !sameGroup(prev, m), last = !sameGroup(m, next))
        }
        submitList(rows)
    }

    private fun sameGroup(a: ClassChat.Message?, b: ClassChat.Message?): Boolean =
        a != null && b != null && !a.deleted && !b.deleted && a.senderId == b.senderId &&
            b.sentAt - a.sentAt < GROUP_GAP_MS && dayKey(a.sentAt) == dayKey(b.sentAt)

    private fun dayKey(time: Long): Long = Calendar.getInstance().run {
        timeInMillis = time
        get(Calendar.YEAR) * 1000L + get(Calendar.DAY_OF_YEAR)
    }

    private fun dayLabel(context: android.content.Context, time: Long): String = when {
        DateUtils.isToday(time) -> context.getString(R.string.ch_today)
        DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.ch_yesterday)
        else -> SimpleDateFormat("MMMM d, yyyy", Locale("fil", "PH")).format(Date(time))
    }

    override fun getItemViewType(position: Int): Int = when (val row = getItem(position)) {
        is Row.Older -> TYPE_OLDER
        is Row.Day -> TYPE_DAY
        is Row.Msg -> when {
            row.message.deleted -> TYPE_REMOVED
            row.message.senderId == myUid -> TYPE_MINE
            else -> TYPE_IN
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = when (viewType) {
            TYPE_OLDER -> R.layout.item_chat_older
            TYPE_DAY -> R.layout.item_chat_day
            TYPE_MINE -> R.layout.item_chat_mine
            TYPE_REMOVED -> R.layout.item_chat_removed
            else -> R.layout.item_chat_in
        }
        return object : RecyclerView.ViewHolder(inflater.inflate(layout, parent, false)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val view = holder.itemView
        when (val row = getItem(position)) {
            is Row.Older -> bindOlder(view, row)
            is Row.Day -> view.findViewById<TextView>(R.id.tvDay).text = row.label
            is Row.Msg -> when (getItemViewType(position)) {
                TYPE_REMOVED -> bindRemoved(view, row)
                TYPE_MINE -> bindMine(view, row)
                else -> bindIn(view, row)
            }
        }
    }

    private fun bindOlder(view: View, row: Row.Older) {
        val button = view.findViewById<TextView>(R.id.btnOlder)
        button.setText(if (row.loading) R.string.ch_loading_older else R.string.ch_older)
        button.isEnabled = !row.loading
        button.setOnClickListener { if (!row.loading) onLoadOlder() }
    }

    private fun timeText(view: View, message: ClassChat.Message): String =
        DateFormat.getTimeFormat(view.context).format(Date(message.sentAt))

    private fun topPadding(view: View, first: Boolean) {
        val dp = view.resources.displayMetrics.density
        view.setPadding(view.paddingLeft, ((if (first) 10 else 3) * dp).toInt(), view.paddingRight, view.paddingBottom)
    }

    private fun bindIn(view: View, row: Row.Msg) {
        val m = row.message
        val context = view.context
        topPadding(view, row.first)

        view.findViewById<View>(R.id.nameRow).visibility = if (row.first) View.VISIBLE else View.GONE
        // Teachers show as "Dela Cruz, Juan · GURO", like the students' "Last, First".
        val name = if (m.isTeacher) ClassChat.lastFirst(m.senderName) else m.senderName
        view.findViewById<TextView>(R.id.tvName).text = name
        view.findViewById<View>(R.id.tvTeacherTag).visibility = if (m.isTeacher) View.VISIBLE else View.GONE

        val avatar = view.findViewById<TextView>(R.id.tvAvatar)
        avatar.visibility = if (row.last) View.VISIBLE else View.INVISIBLE
        avatar.text = m.senderName.trim().firstOrNull()?.uppercase() ?: "?"
        val avatarColor = if (m.isTeacher) ContextCompat.getColor(context, R.color.ch_teacher)
        else AVATAR_COLORS[Math.floorMod(m.senderId.hashCode(), AVATAR_COLORS.size)]
        avatar.backgroundTintList = ColorStateList.valueOf(avatarColor)

        val bubble = view.findViewById<TextView>(R.id.tvBubble)
        bubble.text = m.text
        bubble.setBackgroundResource(if (m.isTeacher) R.drawable.ch_bubble_teacher else R.drawable.ch_bubble_in)

        val time = view.findViewById<TextView>(R.id.tvTime)
        time.visibility = if (row.last) View.VISIBLE else View.GONE
        time.text = timeText(view, m)

        view.contentDescription = "$name${if (m.isTeacher) " (" + context.getString(R.string.ch_teacher_tag) + ")" else ""}: ${m.text}, ${timeText(view, m)}"
    }

    private fun bindMine(view: View, row: Row.Msg) {
        val m = row.message
        topPadding(view, row.first)
        view.findViewById<TextView>(R.id.tvBubble).text = m.text

        val time = view.findViewById<TextView>(R.id.tvTime)
        time.visibility = if (row.last || m.pending) View.VISIBLE else View.GONE
        time.text = if (m.pending) view.context.getString(R.string.ch_sending) else timeText(view, m)

        view.contentDescription = "${view.context.getString(R.string.ch_you)}: ${m.text}, ${time.text}"
        view.setOnLongClickListener {
            if (ClassChat.canDelete(m, myUid)) {
                onLongPressMine(m)
                true
            } else false
        }
    }

    private fun bindRemoved(view: View, row: Row.Msg) {
        val m = row.message
        val mine = m.senderId == myUid
        val pill = view.findViewById<TextView>(R.id.tvRemoved)
        pill.setText(if (m.deletedByTeacher) R.string.ch_removed_teacher else R.string.ch_removed_self)
        pill.layoutParams = (pill.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            marginStart = if (mine) 0 else (40 * view.resources.displayMetrics.density).toInt()
        }
    }
}
