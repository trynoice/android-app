package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.R

class SettingsRepository internal constructor(context: Context) {

  companion object {
    const val APP_THEME_LIGHT = 0
    const val APP_THEME_DARK = 1
    const val APP_THEME_SYSTEM_DEFAULT = 2

    private lateinit var instance: SettingsRepository

    fun getInstance(context: Context): SettingsRepository {
      if (!this::instance.isInitialized) {
        instance = SettingsRepository(context)
      }

      return instance
    }
  }

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
  private val resources = context.applicationContext.resources

  /**
   * Gets user setting for current app theme.
   * Returns one of [APP_THEME_LIGHT], [APP_THEME_DARK] or [APP_THEME_SYSTEM_DEFAULT]
   */
  fun getAppTheme(): Int {
    return prefs.getInt(resources.getString(R.string.app_theme_key), APP_THEME_SYSTEM_DEFAULT)
  }

  /**
   * Gets user setting for app theme and converts it into its corresponding value from
   * array ([AppCompatDelegate.MODE_NIGHT_NO], [AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM],
   * [AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM]).
   */
  fun getAppThemeAsNightMode(): Int {
    return when (getAppTheme()) {
      APP_THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
      APP_THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
      APP_THEME_SYSTEM_DEFAULT -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
      else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    }
  }

  /**
   * Sets user setting for current app theme.
   * @param newTheme should be one of [APP_THEME_LIGHT], [APP_THEME_DARK] or [APP_THEME_SYSTEM_DEFAULT]
   */
  fun setAppTheme(newTheme: Int) {
    prefs.edit { putInt(resources.getString(R.string.app_theme_key), newTheme) }
  }

  /**
   * Returns the current value of switch preference with key [R.string.saved_presets_as_home_screen_key].
   */
  fun shouldDisplaySavedPresetsAsHomeScreen(): Boolean {
    return prefs.getBoolean(resources.getString(R.string.saved_presets_as_home_screen_key), false)
  }

  /**
   * Returns the value of [R.string.sound_fade_duration_key] preference in milliseconds.
   */
  fun getSoundFadeDurationInMillis(): Long {
    return prefs.getInt(resources.getString(R.string.sound_fade_duration_key), 1) * 1000L
  }
}
