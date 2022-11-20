package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

  private lateinit var prefs: SharedPreferences
  private lateinit var prefsEditor: SharedPreferences.Editor
  private lateinit var context: Context
  private lateinit var mockCrashlyticsProvider: CrashlyticsProvider
  private lateinit var mockAnalyticsProvider: AnalyticsProvider

  @OverrideMockKs(InjectionLookupType.BY_NAME)
  private lateinit var settingsRepository: SettingsRepository

  @Before
  fun setup() {
    prefsEditor = mockk {
      every { apply() } returns Unit
    }

    prefs = mockk {
      every { edit() } returns prefsEditor
    }

    context = ApplicationProvider.getApplicationContext()
    mockCrashlyticsProvider = mockk(relaxed = true)
    mockAnalyticsProvider = mockk(relaxed = true)
    settingsRepository = SettingsRepository(context, mockCrashlyticsProvider, mockAnalyticsProvider)
    MockKAnnotations.init(this)
  }

  @Test
  fun getAppTheme() {
    val inputs = arrayOf(
      SettingsRepository.APP_THEME_DARK,
      SettingsRepository.APP_THEME_LIGHT,
      SettingsRepository.APP_THEME_SYSTEM_DEFAULT
    )

    for (input in inputs) {
      every { prefs.getInt(context.getString(R.string.app_theme_key), any()) } returns input
      assertEquals(input, settingsRepository.getAppTheme())
    }
  }

  @Test
  fun getAppThemeAsNightMode() {
    val inputs = arrayOf(
      SettingsRepository.APP_THEME_DARK,
      SettingsRepository.APP_THEME_LIGHT,
      SettingsRepository.APP_THEME_SYSTEM_DEFAULT
    )

    val outputs = arrayOf(
      AppCompatDelegate.MODE_NIGHT_YES,
      AppCompatDelegate.MODE_NIGHT_NO,
      AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )

    for (i in inputs.indices) {
      every { prefs.getInt(context.getString(R.string.app_theme_key), any()) } returns inputs[i]
      assertEquals(outputs[i], settingsRepository.getAppThemeAsNightMode())
    }
  }

  @Test
  fun setAppTheme() {
    val inputs = arrayOf(
      SettingsRepository.APP_THEME_DARK,
      SettingsRepository.APP_THEME_LIGHT,
      SettingsRepository.APP_THEME_SYSTEM_DEFAULT
    )

    every { prefsEditor.putInt(any(), any()) } returns prefsEditor
    for (input in inputs) {
      settingsRepository.setAppTheme(input)
      verify(exactly = 1) { prefsEditor.putInt(context.getString(R.string.app_theme_key), input) }
    }
  }

  @Test
  fun shouldUseMaterialYouColors() {
    val inputs = arrayOf(null, true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.use_material_you_colors_key), any())
      } answers { input ?: secondArg() }

      assertEquals(
        input ?: context.resources.getBoolean(R.bool.use_material_you_colors_default_value),
        settingsRepository.shouldUseMaterialYouColors(),
      )
    }
  }

  @Test
  fun shouldDisplayPresetsAsHomeScreen() {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.presets_as_home_screen_key), any())
      } returns input

      assertEquals(input, settingsRepository.shouldDisplayPresetsAsHomeScreen())
    }
  }

  @Test
  fun getSoundFadeInDurationAsFlow() = runTest {
    val inputs = arrayOf(null, 2, 4, 7)
    for (input in inputs) {
      every {
        prefs.getInt(context.getString(R.string.sound_fade_in_duration_key), any())
      } answers { input ?: secondArg() }

      assertEquals(
        (input ?: context.resources.getInteger(R.integer.default_fade_in_duration_seconds)).seconds,
        settingsRepository.getSoundFadeInDurationAsFlow().firstOrNull(),
      )
    }
  }

  @Test
  fun getSoundFadeOutDurationAsFlow() = runTest {
    val inputs = arrayOf(null, 2, 4, 7)
    for (input in inputs) {
      every {
        prefs.getInt(context.getString(R.string.sound_fade_out_duration_key), any())
      } answers { input ?: secondArg() }

      assertEquals(
        (input
          ?: context.resources.getInteger(R.integer.default_fade_out_duration_seconds)).seconds,
        settingsRepository.getSoundFadeOutDurationAsFlow().firstOrNull(),
      )
    }
  }

  @Test
  fun shouldIgnoreAudioFocusChangesAsFlow() = runTest {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.ignore_audio_focus_changes_key), any())
      } returns input

      assertEquals(input, settingsRepository.shouldIgnoreAudioFocusChangesAsFlow().firstOrNull())
    }
  }

  @Test
  fun shouldDisplaySoundIconsAsFlow() = runTest {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.should_display_sound_icons_key), any())
      } returns input

      assertEquals(input, settingsRepository.shouldDisplaySoundIconsAsFlow().firstOrNull())
    }
  }

  @Test
  fun setShouldShareUsageData() {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      settingsRepository.setShouldShareUsageData(input)
      verify(exactly = 1) {
        mockCrashlyticsProvider.setCollectionEnabled(input)
        mockAnalyticsProvider.setCollectionEnabled(input)
      }
    }
  }

  @Test
  fun isMediaButtonsEnabledAsFlow() = runTest {
    val inputs = arrayOf(null, true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.enable_media_buttons_key), any())
      } answers { input ?: secondArg() }

      assertEquals(
        input ?: context.resources.getBoolean(R.bool.enable_media_buttons_default_value),
        settingsRepository.isMediaButtonsEnabledAsFlow().firstOrNull(),
      )
    }
  }

  @Test
  fun setAudioQuality() {
    val inputs = arrayOf(SettingsRepository.AudioQuality.LOW, SettingsRepository.AudioQuality.HIGH)
    every { prefsEditor.putString(any(), any()) } returns prefsEditor
    for (input in inputs) {
      settingsRepository.setAudioQuality(input)
      verify(exactly = 1) {
        prefsEditor.putString(context.getString(R.string.audio_bitrate_key), input.bitrate)
      }
    }
  }

  @Test
  fun getAudioQualityAsFlow() = runTest {
    val inputs = arrayOf(
      null,
      SettingsRepository.AudioQuality.LOW.bitrate,
      SettingsRepository.AudioQuality.HIGH.bitrate,
    )

    val outputs = arrayOf(
      SettingsRepository.AudioQuality.MEDIUM,
      SettingsRepository.AudioQuality.LOW,
      SettingsRepository.AudioQuality.HIGH,
    )

    for (i in inputs.indices) {
      every {
        prefs.getString(context.getString(R.string.audio_bitrate_key), any())
      } answers { inputs[i] ?: secondArg() }

      assertEquals(outputs[i], settingsRepository.getAudioQualityAsFlow().firstOrNull())
    }
  }

  @Test
  fun getAlarmReminderMaxDuration() {
    val inputs = arrayOf(null, 2, 4, 7)
    for (input in inputs) {
      every {
        prefs.getInt(context.getString(R.string.alarm_ringer_max_duration_key), any())
      } answers { input ?: secondArg() }

      assertEquals(
        (input ?: context.resources.getInteger(R.integer.default_alarm_ringer_max_duration_minutes))
          .minutes,
        settingsRepository.getAlarmRingerMaxDuration(),
      )
    }
  }

  @Test
  fun getAlarmSnoozeDuration() {
    val inputs = arrayOf(null, 2, 4, 7)
    for (input in inputs) {
      every {
        prefs.getInt(context.getString(R.string.alarm_snooze_length_key), any())
      } answers { input ?: secondArg() }

      assertEquals(
        (input ?: context.resources.getInteger(R.integer.default_alarm_snooze_length_minutes))
          .minutes,
        settingsRepository.getAlarmSnoozeDuration(),
      )
    }
  }

  @Test
  fun getAlarmVolumeRampDuration() {
    val inputs = arrayOf(null, 2, 4, 7)
    for (input in inputs) {
      every {
        prefs.getInt(context.getString(R.string.alarm_gradually_increase_volume_key), any())
      } answers { input ?: secondArg() }

      val defaultMinutes = context.resources
        .getInteger(R.integer.default_alarm_gradually_increase_volume_minutes)

      assertEquals(
        (input ?: defaultMinutes).minutes,
        settingsRepository.getAlarmVolumeRampDuration(),
      )
    }
  }
}
