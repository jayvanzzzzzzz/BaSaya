package com.example.basaya.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Light / dark choice from the Profile screen. "System" (the default) follows the phone.
 * Saved on the phone and applied in [com.example.basaya.BaSayaApp] before any screen opens.
 */
enum class ThemeMode(val nightMode: Int, private val systemNightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, UiModeManager.MODE_NIGHT_AUTO),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO, UiModeManager.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES, UiModeManager.MODE_NIGHT_YES);

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
            tellSystem(context, mode)
            AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        }

        fun applySaved(context: Context) {
            val mode = saved(context)
            tellSystem(context, mode)
            AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        }

        /**
         * Android 12+ draws the launch window and its own splash before any app code runs, using the
         * night mode it has on file for the app. Keeping that in step means a child who picked Dark on
         * a light phone (or the reverse) doesn't see a flash of the other theme at launch.
         */
        private fun tellSystem(context: Context, mode: ThemeMode) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode.systemNightMode)
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
