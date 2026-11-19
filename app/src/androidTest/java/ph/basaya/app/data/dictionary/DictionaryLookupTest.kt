package ph.basaya.app.data.dictionary

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.TeacherDictionaryEntity

@RunWith(AndroidJUnit4::class)
class DictionaryLookupTest {
    @Test fun cachedCapitalizedWordMatchesEveryCase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.teacherDictionaryDao()
            dao.upsertAll(listOf(TeacherDictionaryEntity(
                key = "Nagbigay",
                word = "Nagbigay",
                accent = "",
                sensesJson = "[]",
                synonymsJson = "[]",
                hidden = false,
                fixedSyllables = ""
            )))
            for (spelling in listOf("Nagbigay", "nagbigay", "NAGBIGAY", "nAgBiGaY")) {
                assertEquals("Nagbigay", dao.getByKey(DictionaryWords.normalize(spelling))?.word)
            }
        } finally {
            database.close()
        }
    }
}
