package com.example.basaya

import android.app.Application
import com.example.basaya.ui.theme.ThemeMode

class BaSayaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Before any screen opens, so the first frame already has the right theme.
        ThemeMode.applySaved(this)
    }
}
