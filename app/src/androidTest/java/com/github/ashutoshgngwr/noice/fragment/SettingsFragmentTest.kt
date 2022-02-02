package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.HiltFragmentScenario
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var fragmentScenario: HiltFragmentScenario<SettingsFragment>

  @BindValue
  internal lateinit var mockPresetRepository: PresetRepository

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @Before
  fun setup() {
    hiltRule.inject()
    mockPresetRepository = mockk()
    fragmentScenario = launchFragmentInHiltContainer()
  }

  @Test
  fun testExportPresets() {
    val exportData = "test-preset-data"
    every { mockPresetRepository.writeTo(any()) } answers {
      firstArg<OutputStream>().write(exportData.toByteArray())
    }

    // skipping the part where we click the preference and then select a file
    val file = File.createTempFile(
      "test-export",
      null,
      ApplicationProvider.getApplicationContext<Context>().cacheDir
    )

    try {
      fragmentScenario.onFragment { it.onCreateDocumentResult(Uri.fromFile(file)) }
      assertEquals(exportData, file.readText())
    } finally {
      file.delete()
    }
  }

  @Test
  fun testImportPresets() {
    val importData = "test-preset-data"
    every { mockPresetRepository.readFrom(any()) } answers {
      assertEquals(importData, firstArg<InputStream>().readBytes().decodeToString())
    }

    val file = File.createTempFile(
      "test-export",
      null,
      ApplicationProvider.getApplicationContext<Context>().cacheDir
    )

    try {
      file.writeText(importData)
      fragmentScenario.onFragment { it.onOpenDocumentResult(Uri.fromFile(file)) }
      verify(exactly = 1) { mockPresetRepository.readFrom(any()) }
    } finally {
      file.delete()
    }
  }

  @Test
  fun testRemoveAllAppShortcuts() {
    mockkStatic(ShortcutManagerCompat::class)
    onView(withId(androidx.preference.R.id.recycler_view))
      .perform(
        RecyclerViewActions.actionOnItem<androidx.preference.PreferenceViewHolder>(
          hasDescendant(withText(R.string.remove_all_app_shortcuts)), click()
        )
      )

    EspressoX.onViewInDialog(withId(R.id.positive))
      .perform(click())

    verify(exactly = 1) { ShortcutManagerCompat.removeAllDynamicShortcuts(any()) }
  }

  @Test
  fun testAppThemePreference() {
    val nightModes = arrayOf(
      AppCompatDelegate.MODE_NIGHT_NO,
      AppCompatDelegate.MODE_NIGHT_YES,
      AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )

    val prefSummary = arrayOf(
      R.string.app_theme_light,
      R.string.app_theme_dark,
      R.string.app_theme_system_default
    )

    val context = ApplicationProvider.getApplicationContext<Context>()
    val themes = context.resources.getStringArray(R.array.app_themes)
    for (i in themes.indices) {
      onView(withId(androidx.preference.R.id.recycler_view))
        .perform(
          RecyclerViewActions.actionOnItem<androidx.preference.PreferenceViewHolder>(
            hasDescendant(withText(R.string.app_theme)), click()
          )
        )

      EspressoX.onViewInDialog(withText(themes[i]))
        .check(matches(isDisplayed()))
        .perform(click())

      // wait for activity to be recreated.
      onView(withText(R.string.app_theme)).check(matches(isDisplayed()))
      onView(withText(prefSummary[i])).check(matches(isDisplayed()))
      assertEquals(nightModes[i], settingsRepository.getAppThemeAsNightMode())
    }
  }
}
