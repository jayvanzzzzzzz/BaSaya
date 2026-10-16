package com.example.basaya.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Light / dark choice from the Profile screen. "System" (the default) follows the phone.
 * Saved on the phone and applied in [com.example.basaya.BaSayaApp] before any screen opens.
 */
enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        private const val PREFS = "theme_mode"
        private const val KEY = "mode"

        fun saved(context: Context): ThemeMode {
            val name = prefs(context).getString(KEY, null)
            return entries.firstOrNull { it.name == name } ?: SYSTEM
        }

        /** Saves the choice and re-themes every open screen. */
        fun choose(context: Context, mode: ThemeMode) {
            prefs(context).edit().putString(KEY, mode.name).apply()
            AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        }

        fun applySaved(context: Context) {
            AppCompatDelegate.setDefaultNightMode(saved(context).nightMode)
        }

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}

/** True while the dark ("Night Sky") theme is showing. */
fun Context.isNightTheme(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** Dark status-bar icons on light screens, light icons in the dark theme. */
fun Activity.useDarkStatusBarIconsInLightTheme() {
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightTheme()
}
