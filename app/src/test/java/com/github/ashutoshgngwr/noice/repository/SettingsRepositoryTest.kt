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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
    prefsEditor = mockk()
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
  fun testGetAppTheme() {
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
  fun testGetAppThemeAsNightMode() {
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
  fun testSetAppTheme() {
    val inputs = arrayOf(
      SettingsRepository.APP_THEME_DARK,
      SettingsRepository.APP_THEME_LIGHT,
      SettingsRepository.APP_THEME_SYSTEM_DEFAULT
    )

    every { prefsEditor.putInt(any(), any()) } returns prefsEditor
    every { prefsEditor.apply() } returns Unit
    for (input in inputs) {
      settingsRepository.setAppTheme(input)
      verify(exactly = 1) { prefsEditor.putInt(context.getString(R.string.app_theme_key), input) }
    }
  }

  @Test
  fun testShouldDisplayPresetsAsHomeScreen() {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.presets_as_home_screen_key), any())
      } returns input

      assertEquals(input, settingsRepository.shouldDisplayPresetsAsHomeScreen())
    }
  }

  @Test
  fun testGetSoundFadeDurationInMillis() {
    val inputs = arrayOf(2, 4, 7)
    for (input in inputs) {
      every {
        prefs.getInt(context.getString(R.string.sound_fade_duration_key), any())
      } returns input

      every {
        prefs.getInt(context.getString(R.string.sound_fade_in_duration_key), any())
      } answers { secondArg() }

      assertEquals(input * 1000L, settingsRepository.getSoundFadeInDurationMillis())
    }
  }

  @Test
  fun testShouldIgnoreAudioFocusChanges() {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.ignore_audio_focus_changes_key), any())
      } returns input

      assertEquals(input, settingsRepository.shouldIgnoreAudioFocusChanges())
    }
  }

  @Test
  fun testShouldDisplaySoundIcons() {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.should_display_sound_icons_key), any())
      } returns input

      assertEquals(input, settingsRepository.shouldDisplaySoundIcons())
    }
  }

  @Test
  fun testSetShouldShareUsageData() {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      settingsRepository.setShouldShareUsageData(input)
      verify(exactly = 1) {
        mockCrashlyticsProvider.setCollectionEnabled(input)
        mockAnalyticsProvider.setCollectionEnabled(input)
      }
    }
  }
}
