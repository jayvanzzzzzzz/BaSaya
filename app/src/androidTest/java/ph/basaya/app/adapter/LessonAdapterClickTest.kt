package ph.basaya.app.adapter

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ph.basaya.app.R
import ph.basaya.app.data.entity.LessonEntity

@RunWith(AndroidJUnit4::class)
class LessonAdapterClickTest {
    @Test fun completedDownloadCanBeOpenedWithoutRebindingTheCard() = onMain {
        val fixture = Card(lesson(downloaded = false))

        fixture.adapter.finishBulkLesson("lesson", success = true)
        fixture.bindDownloadPayload()
        fixture.holder.itemView.performClick()

        assertEquals(true, fixture.clicked?.isDownloaded)
    }

    @Test fun refreshedDownloadStateIsUsedByTheExistingClickListener() = onMain {
        val fixture = Card(lesson(downloaded = false))

        fixture.adapter.updateList(listOf(lesson(downloaded = true)))
        fixture.bindDownloadPayload()
        fixture.holder.itemView.performClick()

        assertEquals(true, fixture.clicked?.isDownloaded)
    }

    @Test fun removedDownloadIsNotOpenedUsingItsOldDownloadedState() = onMain {
        val fixture = Card(lesson(downloaded = true))

        fixture.adapter.updateList(listOf(lesson(downloaded = false)))
        fixture.bindDownloadPayload()
        fixture.holder.itemView.performClick()

        assertEquals(false, fixture.clicked?.isDownloaded)
    }

    @Test fun removedLessonIsIgnoredByAnExistingCard() = onMain {
        val fixture = Card(lesson(downloaded = true))

        fixture.adapter.updateList(emptyList())
        fixture.holder.itemView.performClick()

        assertNull(fixture.clicked)
    }

    @Test fun recycledCardOpensTheCurrentLessonAfterItsDownloadFinishes() = onMain {
        val fixture = Card(lesson(downloaded = false))
        val next = lesson(downloaded = false).copy(id = "next")
        fixture.adapter.updateList(listOf(next))
        fixture.adapter.onBindViewHolder(fixture.holder, 0)

        fixture.adapter.finishBulkLesson("next", success = true)
        fixture.bindDownloadPayload()
        fixture.holder.itemView.performClick()

        assertEquals("next", fixture.clicked?.id)
        assertEquals(true, fixture.clicked?.isDownloaded)
    }

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private fun lesson(downloaded: Boolean) = LessonEntity(
        id = "lesson", userId = "test-student", classId = "test-class", title = "Test lesson",
        description = "", difficulty = "", order = 1, unlocked = true, isDownloaded = downloaded
    )

    /** Real Android views and the production adapter; no network or saved lesson data is changed. */
    private class Card(initial: LessonEntity) {
        var clicked: LessonEntity? = null
        val adapter = LessonAdapter(
            listOf(initial), onItemClick = { clicked = it },
            onDownloadClick = { _, _, _ -> }, onRemoveDownloadClick = { _, _ -> }
        )
        private val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.Theme_BaSaya
        )
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        init { adapter.onBindViewHolder(holder, 0) }

        fun bindDownloadPayload() {
            adapter.onBindViewHolder(holder, 0, listOf("download_progress"))
        }
    }
}
