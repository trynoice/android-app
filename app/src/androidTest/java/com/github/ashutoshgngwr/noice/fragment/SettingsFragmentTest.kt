package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFragmentTest {
  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var fragmentScenario: FragmentScenario<SettingsFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
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
      onView(withText(R.string.app_theme)).perform(click())
      onView(withText(themes[i]))
        .inRoot(isDialog())
        .check(matches(isDisplayed()))
        .perform(click())

      // wait for activity to be recreated.
      onView(withText(R.string.app_theme)).check(matches(isDisplayed()))
      onView(withText(prefSummary[i])).check(matches(isDisplayed()))
      assertEquals(nightModes[i], SettingsRepository.getInstance(context).getAppThemeAsNightMode())
    }
  }
}
