package ph.basaya.app.data.progress

import org.junit.Assert.*
import org.junit.Test
import ph.basaya.app.data.entity.CrosswordProgress

class CrosswordProgressStateTest {
    private val key = "alice:session:lesson"
    @Test fun serverResetClearsAdvancedLocalProgress() {
        val local = CrosswordProgress(key, 4, 3, "OLD")
        assertEquals(CrosswordProgress(key, 1, 0, ""),
            CrosswordProgressState.reconcile(key, local, 0, emptyList(), true))
    }
    @Test fun serverResetClearsWordsAtSameLevel() {
        assertEquals("", CrosswordProgressState.reconcile(key,
            CrosswordProgress(key, 1, 0, "OLD"), 0, emptyList(), true).foundWords)
    }
    @Test fun remoteProgressAndAcceptedWordsReplaceLocalState() {
        assertEquals(CrosswordProgress(key, 3, 2, "NEW"),
            CrosswordProgressState.reconcile(key, CrosswordProgress(key, 1, 0, "OLD"),
                2, listOf("NEW", "NEW"), true))
    }
    @Test fun cachePreservesOfflineWork() {
        val local = CrosswordProgress(key, 4, 3, "OFFLINE")
        assertEquals(local, CrosswordProgressState.reconcile(key, local, 0, emptyList(), false))
    }
    @Test fun anotherStudentAndLegacyRowsAreNeverReused() {
        for (oldKey in listOf("bob:session:lesson", "lesson")) {
            assertEquals(CrosswordProgress(key, 1, 0, ""),
                CrosswordProgressState.reconcile(key, CrosswordProgress(oldKey, 4, 3, "OTHER"),
                    0, emptyList(), false))
        }
    }
}
