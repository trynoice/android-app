package com.github.ashutoshgngwr.noice.fragment

import androidx.annotation.IdRes
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HomeFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var playbackServiceControllerMock: SoundPlaybackService.Controller

  @BindValue
  internal lateinit var settingsRepositoryMock: SettingsRepository

  @Before
  fun setUp() {
    playbackServiceControllerMock = mockk(relaxed = true)
    settingsRepositoryMock = mockk(relaxed = true)
  }

  @Test
  fun startDestination() {
    launchFragmentInHiltContainer<HomeFragment>().use {
      onView(withId(R.id.bottom_nav))
        .check(matches(isDisplayed()))
        .check(matches(EspressoX.withBottomNavSelectedItem(R.id.library)))

      it.onFragment { f ->
        val c = Navigation.findNavController(f.requireActivity(), R.id.home_nav_host_fragment)
        assertEquals(R.id.library, c.currentDestination?.id)
      }
    }

    every { settingsRepositoryMock.shouldDisplayPresetsAsHomeScreen() } returns true
    launchFragmentInHiltContainer<HomeFragment>().use {
      onView(withId(R.id.bottom_nav))
        .check(matches(isDisplayed()))
        .check(matches(EspressoX.withBottomNavSelectedItem(R.id.presets)))

      it.onFragment { f ->
        val c = Navigation.findNavController(f.requireActivity(), R.id.home_nav_host_fragment)
        assertEquals(R.id.presets, c.currentDestination?.id)
      }
    }
  }

  @Test
  fun playbackController() {
    data class TestCase(
      @IdRes val currentDestinationId: Int,
      val expectVisible: Boolean,
    )

    listOf(
      TestCase(
        currentDestinationId = R.id.library,
        expectVisible = true,
      ),
      TestCase(
        currentDestinationId = R.id.alarms,
        expectVisible = false,
      ),
      TestCase(
        currentDestinationId = R.id.account,
        expectVisible = false,
      ),
      TestCase(
        currentDestinationId = R.id.presets,
        expectVisible = true,
      ),
      TestCase(
        currentDestinationId = R.id.sleep_timer,
        expectVisible = true,
      ),
    ).forEach { testCase ->
      every { playbackServiceControllerMock.getState() } returns flowOf(SoundPlayerManager.State.PLAYING)
      launchFragmentInHiltContainer<HomeFragment>().use {
        onView(withId(testCase.currentDestinationId))
          .check(matches(isDisplayed()))
          .perform(click())

        onView(withId(R.id.playback_controller))
          .check(matches(if (testCase.expectVisible) isDisplayed() else not(isDisplayed())))
      }
    }
  }
}
