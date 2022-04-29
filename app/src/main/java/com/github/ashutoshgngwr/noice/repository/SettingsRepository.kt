package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.keysFlow
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository] implements the data access layer for storing various user preferences.
 */
@Singleton
class SettingsRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val crashlyticsProvider: CrashlyticsProvider,
  private val analyticsProvider: AnalyticsProvider,
) {

  companion object {
    internal const val APP_THEME_LIGHT = 0
    internal const val APP_THEME_DARK = 1
    internal const val APP_THEME_SYSTEM_DEFAULT = 2
  }

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  /**
   * Gets user setting for current app theme.
   * Returns one of [APP_THEME_LIGHT], [APP_THEME_DARK] or [APP_THEME_SYSTEM_DEFAULT]
   */
  fun getAppTheme(): Int {
    return prefs.getInt(context.getString(R.string.app_theme_key), APP_THEME_SYSTEM_DEFAULT)
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
    prefs.edit { putInt(context.getString(R.string.app_theme_key), newTheme) }
  }

  /**
   * Returns the current value of switch preference with key [R.string.presets_as_home_screen_key].
   */
  fun shouldDisplayPresetsAsHomeScreen(): Boolean {
    return prefs.getBoolean(context.getString(R.string.presets_as_home_screen_key), false)
  }

  /**
   * Returns the value of [R.string.sound_fade_in_duration_key] preference in milliseconds.
   */
  fun getSoundFadeInDurationMillis(): Long {
    return prefs.getInt(context.getString(R.string.sound_fade_in_duration_key), 1) * 1000L
  }

  /**
   * Returns a [Flow] for key [R.string.sound_fade_in_duration_key] that listens for changes and
   * emits its latest value.
   */
  fun getSoundFadeInDurationMillisAsFlow(): Flow<Long> {
    return keyFlow(R.string.sound_fade_in_duration_key).map { getSoundFadeInDurationMillis() }
  }

  /**
   * Returns the value of switch preference with key [R.string.ignore_audio_focus_changes_key].
   */
  fun shouldIgnoreAudioFocusChanges(): Boolean {
    return prefs.getBoolean(context.getString(R.string.ignore_audio_focus_changes_key), false)
  }

  /**
   * Returns a [Flow] for key [R.string.ignore_audio_focus_changes_key] that listens for changes and
   * emits its latest value.
   */
  fun shouldIgnoreAudioFocusChangesAsFlow(): Flow<Boolean> {
    return keyFlow(R.string.ignore_audio_focus_changes_key).map { shouldIgnoreAudioFocusChanges() }
  }

  /**
   * Returns the value of switch preference with key [R.string.should_display_sound_icons_key].
   */
  fun shouldDisplaySoundIcons(): Boolean {
    return prefs.getBoolean(context.getString(R.string.should_display_sound_icons_key), true)
  }

  /**
   * Enables/disables the data collection preferences on the [AnalyticsProvider] and the
   * [CrashlyticsProvider]. Both providers persist these settings across app sessions, so there's no
   * need to handle their persistence.
   */
  fun setShouldShareUsageData(enabled: Boolean) {
    crashlyticsProvider.setCollectionEnabled(enabled)
    analyticsProvider.setCollectionEnabled(enabled)
  }

  /**
   * Returns the value of switch preference with key [R.string.enable_media_buttons_key]
   */
  fun isMediaButtonsEnabled(): Boolean {
    return prefs.getBoolean(
      context.getString(R.string.enable_media_buttons_key), true
    )
  }

  /**
   * Returns a [Flow] for key [R.string.enable_media_buttons_key] that listens for changes and emits
   * its latest value.
   */
  fun isMediaButtonsEnabledAsFlow(): Flow<Boolean> {
    return keyFlow(R.string.enable_media_buttons_key).map { isMediaButtonsEnabled() }
  }

  /**
   * Sets the value of list preference with key [R.string.audio_quality_key].
   */
  fun setMaxAudioBitrate(bitrate: Int) {
    prefs.edit { putInt(context.getString(R.string.audio_quality_key), bitrate) }
  }

  /**
   * Returns the value of list preference with key [R.string.audio_quality_key].
   */
  fun getMaxAudioBitrate(): Int {
    return prefs.getInt(context.getString(R.string.audio_quality_key), 128000)
  }

  /**
   * Returns a [Flow] for key [R.string.audio_quality_key] that listens for changes and emits its
   * latest value.
   */
  fun getMaxAudioBitrateAsFlow(): Flow<Int> {
    return keyFlow(R.string.audio_quality_key).map { getMaxAudioBitrate() }
  }

  private fun keyFlow(@StringRes keyStrRes: Int): Flow<String> {
    val key = context.getString(keyStrRes)
    return prefs.keysFlow()
      .filter { it == key }
      .onStart { emit(key) } // immediately emit a change as soon as the flow collection starts.
  }
}
