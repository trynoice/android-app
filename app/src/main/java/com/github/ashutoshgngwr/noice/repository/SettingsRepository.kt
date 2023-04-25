package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.keyFlow
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.metrics.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.models.AudioQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * [SettingsRepository] implements the data access layer for storing various user preferences.
 */
@Singleton
class SettingsRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val crashlyticsProvider: CrashlyticsProvider,
  private val analyticsProvider: AnalyticsProvider,
) {

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
   * Returns the current value of switch preference with key [R.string.use_material_you_colors_key].
   */
  fun shouldUseMaterialYouColors(): Boolean {
    return prefs.getBoolean(
      context.getString(R.string.use_material_you_colors_key),
      context.resources.getBoolean(R.bool.use_material_you_colors_default_value),
    )
  }

  /**
   * Returns the current value of switch preference with key [R.string.presets_as_home_screen_key].
   */
  fun shouldDisplayPresetsAsHomeScreen(): Boolean {
    return prefs.getBoolean(
      context.getString(R.string.presets_as_home_screen_key),
      context.resources.getBoolean(R.bool.presets_as_home_screen_default_value),
    )
  }

  private fun getSoundFadeInDuration(): Duration {
    return prefs.getInt(
      context.getString(R.string.sound_fade_in_duration_key),
      context.resources.getInteger(R.integer.default_fade_in_duration_seconds)
    ).toDuration(DurationUnit.SECONDS)
  }

  /**
   * Returns a [Flow] for key [R.string.sound_fade_in_duration_key] that listens for changes and
   * emits its latest value.
   */
  fun getSoundFadeInDurationAsFlow(): Flow<Duration> {
    return keyFlow(R.string.sound_fade_in_duration_key).map { getSoundFadeInDuration() }
  }

  private fun getSoundFadeOutDuration(): Duration {
    return prefs.getInt(
      context.getString(R.string.sound_fade_out_duration_key),
      context.resources.getInteger(R.integer.default_fade_out_duration_seconds)
    ).toDuration(DurationUnit.SECONDS)
  }

  /**
   * Returns a [Flow] for key [R.string.sound_fade_out_duration_key] that listens for changes and
   * emits its latest value.
   */
  fun getSoundFadeOutDurationAsFlow(): Flow<Duration> {
    return keyFlow(R.string.sound_fade_out_duration_key).map { getSoundFadeOutDuration() }
  }

  private fun shouldIgnoreAudioFocusChanges(): Boolean {
    return prefs.getBoolean(context.getString(R.string.ignore_audio_focus_changes_key), false)
  }

  /**
   * Returns a [Flow] for key [R.string.ignore_audio_focus_changes_key] that listens for changes and
   * emits its latest value.
   */
  fun shouldIgnoreAudioFocusChangesAsFlow(): Flow<Boolean> {
    return keyFlow(R.string.ignore_audio_focus_changes_key).map { shouldIgnoreAudioFocusChanges() }
  }

  private fun shouldDisplaySoundIcons(): Boolean {
    return prefs.getBoolean(
      context.getString(R.string.should_display_sound_icons_key),
      context.resources.getBoolean(R.bool.should_display_sound_icons_default_value),
    )
  }

  /**
   * Returns a [Flow] for key [R.string.should_display_sound_icons_key] that listens for changes and
   * emits its latest value.
   */
  fun shouldDisplaySoundIconsAsFlow(): Flow<Boolean> {
    return keyFlow(R.string.should_display_sound_icons_key).map { shouldDisplaySoundIcons() }
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
  private fun isMediaButtonsEnabled(): Boolean {
    return prefs.getBoolean(
      context.getString(R.string.enable_media_buttons_key),
      context.resources.getBoolean(R.bool.enable_media_buttons_default_value),
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
   * Sets the value of list preference with key [R.string.audio_bitrate_key].
   */
  fun setAudioQuality(quality: AudioQuality) {
    prefs.edit { putString(context.getString(R.string.audio_bitrate_key), quality.bitrate) }
  }

  /**
   * Returns the value of list preference with key [R.string.audio_bitrate_key].
   */
  fun getAudioQuality(): AudioQuality {
    return prefs.getString(context.getString(R.string.audio_bitrate_key), null)
      ?.let { AudioQuality.fromBitrate(it) }
      ?: AudioQuality.MEDIUM
  }

  /**
   * Returns a [Flow] for key [R.string.audio_bitrate_key] that listens for changes and emits its
   * latest value.
   */
  fun getAudioQualityAsFlow(): Flow<AudioQuality> {
    return keyFlow(R.string.audio_bitrate_key).map { getAudioQuality() }
  }

  fun getAlarmRingerMaxDuration(): Duration {
    return prefs.getInt(
      context.getString(R.string.alarm_ringer_max_duration_key),
      context.resources.getInteger(R.integer.default_alarm_ringer_max_duration_minutes),
    ).toDuration(DurationUnit.MINUTES)
  }

  fun getAlarmSnoozeDuration(): Duration {
    return prefs.getInt(
      context.getString(R.string.alarm_snooze_length_key),
      context.resources.getInteger(R.integer.default_alarm_snooze_length_minutes),
    ).toDuration(DurationUnit.MINUTES)
  }

  fun getAlarmVolumeRampDuration(): Duration {
    return prefs.getInt(
      context.getString(R.string.alarm_gradually_increase_volume_key),
      context.resources.getInteger(R.integer.default_alarm_gradually_increase_volume_minutes),
    ).toDuration(DurationUnit.MINUTES)
  }

  private fun keyFlow(@StringRes keyStrRes: Int): Flow<String> {
    return prefs.keyFlow(context.getString(keyStrRes))
  }

  companion object {
    internal const val APP_THEME_LIGHT = 0
    internal const val APP_THEME_DARK = 1
    internal const val APP_THEME_SYSTEM_DEFAULT = 2

    val FREE_AUDIO_QUALITY = AudioQuality.fromBitrate(null)
  }

}
