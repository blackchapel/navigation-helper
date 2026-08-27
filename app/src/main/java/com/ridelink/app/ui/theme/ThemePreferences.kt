package com.ridelink.app.ui.theme

import android.content.Context

/** Persists the user's manual light/dark choice across app restarts. */
class ThemePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDarkTheme(systemDefault: Boolean): Boolean =
        prefs.getBoolean(KEY_IS_DARK, systemDefault)

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_IS_DARK, isDark).apply()
    }

    private companion object {
        const val PREFS_NAME = "ridelink_prefs"
        const val KEY_IS_DARK = "is_dark_theme"
    }
}
