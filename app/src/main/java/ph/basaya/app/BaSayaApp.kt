package ph.basaya.app

import android.app.Application
import ph.basaya.app.data.dictionary.TeacherDictionarySync
import ph.basaya.app.ui.theme.ThemeMode

class BaSayaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Before any screen opens, so the first frame already has the right theme.
        ThemeMode.applySaved(this)
        TeacherDictionarySync.initialize(this)
    }
}
