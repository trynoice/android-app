package com.github.ashutoshgngwr.noice.fragment

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.EspressoX.slide
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SoundPlaybackControllerFragmentTest {

  @get:Rule
  val hiltRulte = HiltAndroidRule(this)

  @BindValue
  internal lateinit var playbackServiceControllerMock: SoundPlaybackService.Controller

  @Before
  fun setUp() {
    playbackServiceControllerMock = mockk(relaxed = true)
  }

  @Test
  fun presetName() {
    val testCases = mapOf(
      Preset(
        id = "preset-0",
        name = "preset-0",
        soundStates = sortedMapOf(
          "sound-0" to 0.05F,
          "sound-1" to 0.1F
        ),
      ) to "preset-0",
      Preset(
        id = "preset-1",
        name = "preset-1",
        soundStates = sortedMapOf(
          "sound-2" to 0.15F,
          "sound-3" to 0.2F,
          "sound-4" to 0.25F,
        ),
      ) to "preset-1",
      null to null
    )

    testCases.forEach { (inputPreset, expectedPresetName) ->
      clearMocks(playbackServiceControllerMock)
      every { playbackServiceControllerMock.getCurrentPreset() } returns flowOf(inputPreset)

      launchFragmentInHiltContainer<SoundPlaybackControllerFragment>().use {
        onView(
          if (expectedPresetName == null)
            withText(R.string.unsaved_preset)
          else
            withText(expectedPresetName)
        ).check(matches(isDisplayed()))
      }
    }
  }

  @Test
  fun playToggleButton() {
    data class TestCase(
      val inputPlayerManagerState: SoundPlayerManager.State,
      val expectPlaying: Boolean,
    )

    listOf(
      TestCase(
        inputPlayerManagerState = SoundPlayerManager.State.PLAYING,
        expectPlaying = true,
      ),
      TestCase(
        inputPlayerManagerState = SoundPlayerManager.State.PAUSING,
        expectPlaying = false,
      ),
      TestCase(
        inputPlayerManagerState = SoundPlayerManager.State.PAUSED,
        expectPlaying = false,
      ),
      TestCase(
        inputPlayerManagerState = SoundPlayerManager.State.STOPPING,
        expectPlaying = false,
      ),
      TestCase(
        inputPlayerManagerState = SoundPlayerManager.State.STOPPED,
        expectPlaying = false,
      ),
    ).forEach { testCase ->
      clearMocks(playbackServiceControllerMock)
      every { playbackServiceControllerMock.getState() } returns flowOf(testCase.inputPlayerManagerState)
      launchFragmentInHiltContainer<SoundPlaybackControllerFragment>().use {
        onView(withId(R.id.play_toggle))
          .check(matches(isDisplayed()))
          .perform(click())

        verify(exactly = 1, timeout = 5000) {
          if (testCase.expectPlaying) {
            playbackServiceControllerMock.pause()
          } else {
            playbackServiceControllerMock.resume()
          }
        }
      }
    }
  }

  @Test
  fun stopButton() {
    launchFragmentInHiltContainer<SoundPlaybackControllerFragment>()
    onView(withId(R.id.stop))
      .check(matches(isDisplayed()))
      .perform(click())

    verify(exactly = 1, timeout = 5000) { playbackServiceControllerMock.stop() }
  }

  @Test
  fun volumeControl() {
    launchFragmentInHiltContainer<SoundPlaybackControllerFragment>()
    onView(withId(R.id.volume))
      .check(matches(isDisplayed()))
      .perform(click())

    onView(withId(R.id.volume_slider))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(slide(0.1F))

    verify(exactly = 1, timeout = 5000) { playbackServiceControllerMock.setVolume(0.1F) }
  }
}
