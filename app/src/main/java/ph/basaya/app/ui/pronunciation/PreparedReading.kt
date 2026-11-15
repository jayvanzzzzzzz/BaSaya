package ph.basaya.app.ui.pronunciation

import android.content.Context
import ph.basaya.app.data.model.PronunciationActivityData
import ph.basaya.app.data.progress.PracticeLessons
import ph.basaya.app.data.repository.PronunciationRepository
import java.util.UUID

/** Single-use handoff of the session already opened on the dashboard. */
internal data class PreparedReading(
    val lessonId: String,
    val session: ReadingSession,
    val content: PronunciationActivityData?
) {
    companion object {
        private var pending: Pair<String, PreparedReading>? = null

        suspend fun prepare(
            context: Context,
            lessonId: String,
            content: PronunciationActivityData? = null
        ): PreparedReading {
            val app = context.applicationContext
            val session = ReadingSession(app, lessonId)
            PracticeLessons.refresh(app, lessonId)
            val loaded = content ?: PronunciationRepository(app).getActivity(lessonId)
            session.open()
            check(session.isCurrent()) { "Nagbago ang account o klase. Buksan muli ang aralin." }
            return PreparedReading(lessonId, session, loaded)
        }

        fun store(reading: PreparedReading): String = UUID.randomUUID().toString().also {
            pending = it to reading
        }

        fun take(token: String?, lessonId: String): PreparedReading? {
            val entry = pending ?: return null
            if (entry.first != token) return null
            pending = null
            return entry.second.takeIf { it.lessonId == lessonId && it.session.isCurrent() }
        }
    }
}
