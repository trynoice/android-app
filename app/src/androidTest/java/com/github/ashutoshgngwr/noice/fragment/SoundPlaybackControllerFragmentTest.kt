package com.github.ashutoshgngwr.noice.fragment

import androidx.annotation.StringRes
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
      every { playbackServiceControllerMock.getState() } returns flowOf(SoundPlayerManager.State.PLAYING)
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
  fun playbackState() {
    data class TestCase(
      val soundPlayerManagerState: SoundPlayerManager.State,
      @StringRes val expectPlaybackStateResId: Int,
      val expectVisibility: Visibility,
    )
    listOf(
      TestCase(
        soundPlayerManagerState = SoundPlayerManager.State.PLAYING,
        expectPlaybackStateResId = R.string.playing,
        expectVisibility = Visibility.VISIBLE,
      ),
      TestCase(
        soundPlayerManagerState = SoundPlayerManager.State.PAUSING,
        expectPlaybackStateResId = R.string.pausing,
        expectVisibility = Visibility.VISIBLE,
      ),
      TestCase(
        soundPlayerManagerState = SoundPlayerManager.State.PAUSED,
        expectPlaybackStateResId = R.string.paused,
        expectVisibility = Visibility.VISIBLE,
      ),
      TestCase(
        soundPlayerManagerState = SoundPlayerManager.State.STOPPING,
        expectPlaybackStateResId = R.string.stopping,
        expectVisibility = Visibility.VISIBLE,
      ),
      TestCase(
        soundPlayerManagerState = SoundPlayerManager.State.STOPPED,
        expectPlaybackStateResId = R.string.stopping,
        expectVisibility = Visibility.GONE,
      ),
    ).forEach { testCase ->
      clearMocks(playbackServiceControllerMock)
      every { playbackServiceControllerMock.getState() } returns flowOf(testCase.soundPlayerManagerState)
      launchFragmentInHiltContainer<SoundPlaybackControllerFragment>().use {
        onView(withId(R.id.playback_state))
          .check(matches(withText(testCase.expectPlaybackStateResId)))

        onView(withId(R.id.root))
          .check(matches(withEffectiveVisibility(testCase.expectVisibility)))
      }
    }
  }

  @Test
  fun playToggleButton() {
    data class TestCase(
      val playbackState: SoundPlayerManager.State,
      val expectPlaying: Boolean,
    )

    listOf(
      TestCase(
        playbackState = SoundPlayerManager.State.PLAYING,
        expectPlaying = true,
      ),
      TestCase(
        playbackState = SoundPlayerManager.State.PAUSING,
        expectPlaying = false,
      ),
      TestCase(
        playbackState = SoundPlayerManager.State.PAUSED,
        expectPlaying = false,
      ),
      TestCase(
        playbackState = SoundPlayerManager.State.STOPPING,
        expectPlaying = false,
      ),
    ).forEach { testCase ->
      clearMocks(playbackServiceControllerMock)
      every { playbackServiceControllerMock.getState() } returns flowOf(testCase.playbackState)
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
    every { playbackServiceControllerMock.getState() } returns flowOf(SoundPlayerManager.State.PLAYING)
    launchFragmentInHiltContainer<SoundPlaybackControllerFragment>()
    onView(withId(R.id.stop))
      .check(matches(isDisplayed()))
      .perform(click())

    verify(exactly = 1, timeout = 5000) { playbackServiceControllerMock.stop() }
  }

  @Test
  fun volumeControl() {
    every { playbackServiceControllerMock.getState() } returns flowOf(SoundPlayerManager.State.PLAYING)
    every { playbackServiceControllerMock.getVolume() } returns flowOf(0.5F)
    launchFragmentInHiltContainer<SoundPlaybackControllerFragment>()
    onView(withId(R.id.volume))
      .check(matches(isDisplayed()))
      .check(matches(withText("50%")))
      .perform(click())

    onView(withId(R.id.volume_slider))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(slide(0.1F))

    verify(exactly = 1, timeout = 5000) { playbackServiceControllerMock.setVolume(0.1F) }
  }
}
