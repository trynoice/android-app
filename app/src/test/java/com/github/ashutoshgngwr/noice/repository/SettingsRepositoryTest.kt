package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.R
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

  @OverrideMockKs(InjectionLookupType.BY_NAME)
  private lateinit var settingsRepository: SettingsRepository

  @Before
  fun setup() {
    prefsEditor = mockk()
    prefs = mockk {
      every { edit() } returns prefsEditor
    }

    context = ApplicationProvider.getApplicationContext()
    settingsRepository = SettingsRepository.newInstance(context)
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
  fun testShouldDisplaySavedPresetsAsHomeScreen() {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      every {
        prefs.getBoolean(context.getString(R.string.saved_presets_as_home_screen_key), any())
      } returns input

      assertEquals(input, settingsRepository.shouldDisplaySavedPresetsAsHomeScreen())
    }
  }

  @Test
  fun testGetSoundFadeDurationInMillis() {
    val inputs = arrayOf(2, 4, 7)
    for (input in inputs) {
      every {
        prefs.getInt(context.getString(R.string.sound_fade_duration_key), any())
      } returns input

      assertEquals(input * 1000L, settingsRepository.getSoundFadeDurationInMillis())
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
}
