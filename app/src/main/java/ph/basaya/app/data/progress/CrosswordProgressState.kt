package ph.basaya.app.data.progress

import ph.basaya.app.data.entity.CrosswordProgress

/** Server-confirmed state wins; cached snapshots cannot erase offline work. */
internal object CrosswordProgressState {
    fun reconcile(key: String, local: CrosswordProgress?, completed: Int,
                  words: List<String>, authoritative: Boolean): CrosswordProgress {
        if (!authoritative && local?.lessonId == key) return local
        return CrosswordProgress(
            lessonId = key,
            currentLevel = completed.coerceAtLeast(0) + 1,
            completedLevel = completed.coerceAtLeast(0),
            foundWords = words.distinct().joinToString(",")
        )
    }
}
