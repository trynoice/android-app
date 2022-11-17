package com.github.ashutoshgngwr.noice.fragment

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.repository.PresetRepository
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
class PlaybackControllerFragmentTest {

  @get:Rule
  val hiltRulte = HiltAndroidRule(this)

  @BindValue
  internal lateinit var presetRepositoryMock: PresetRepository

  @BindValue
  internal lateinit var playbackControllerMock: PlaybackController

  @Before
  fun setUp() {
    presetRepositoryMock = mockk(relaxed = true)
    playbackControllerMock = mockk(relaxed = true)
  }

  @Test
  fun presetName() {
    val presets = listOf(
      Preset(
        name = "preset-0",
        playerStates = arrayOf(
          PlayerState(soundId = "sound-0", volume = 1),
          PlayerState(soundId = "sound-1", volume = 2),
        ),
      ),
      Preset(
        name = "preset-1",
        playerStates = arrayOf(
          PlayerState(soundId = "sound-2", volume = 3),
          PlayerState(soundId = "sound-3", volume = 4),
          PlayerState(soundId = "sound-4", volume = 5),
        ),
      ),
      Preset(
        name = "preset-2",
        playerStates = arrayOf(
          PlayerState(soundId = "sound-5", volume = 6),
        ),
      ),
    )

    mapOf(
      arrayOf(PlayerState(soundId = "sound-6", volume = 10)) to null,
      presets[0].playerStates to "preset-0",
      presets[1].playerStates to "preset-1",
      presets[2].playerStates to "preset-2",
    ).forEach { (inputPlayerStates, expectedPresetName) ->
      clearMocks(presetRepositoryMock, playbackControllerMock)
      every { presetRepositoryMock.listFlow() } returns flowOf(presets)
      every { playbackControllerMock.getPlayerStates() } returns flowOf(inputPlayerStates)

      launchFragmentInHiltContainer<PlaybackControllerFragment>().use {
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
    data class TestCase(val inputPlayerManagerState: PlaybackState, val expectPlaying: Boolean)

    listOf(
      TestCase(
        inputPlayerManagerState = PlaybackState.PLAYING,
        expectPlaying = true,
      ),
      TestCase(
        inputPlayerManagerState = PlaybackState.PAUSING,
        expectPlaying = false,
      ),
      TestCase(
        inputPlayerManagerState = PlaybackState.PAUSED,
        expectPlaying = false,
      ),
      TestCase(
        inputPlayerManagerState = PlaybackState.STOPPING,
        expectPlaying = false,
      ),
      TestCase(
        inputPlayerManagerState = PlaybackState.STOPPED,
        expectPlaying = false,
      ),
    ).forEach { testCase ->
      clearMocks(playbackControllerMock)
      every { playbackControllerMock.getPlayerManagerState() } returns flowOf(testCase.inputPlayerManagerState)
      launchFragmentInHiltContainer<PlaybackControllerFragment>().use {
        onView(withId(R.id.play_toggle))
          .check(matches(isDisplayed()))
          .perform(click())

        verify(exactly = 1, timeout = 5000) {
          if (testCase.expectPlaying) {
            playbackControllerMock.pause()
          } else {
            playbackControllerMock.resume()
          }
        }
      }
    }
  }

  @Test
  fun stopButton() {
    launchFragmentInHiltContainer<PlaybackControllerFragment>()
    onView(withId(R.id.stop))
      .check(matches(isDisplayed()))
      .perform(click())

    verify(exactly = 1, timeout = 5000) { playbackControllerMock.stop() }
  }
}
