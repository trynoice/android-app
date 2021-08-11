package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.media.AudioAttributesCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmRingerActivityTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  @Before
  fun setup() {
    mockkObject(PlaybackController)
    every { PlaybackController.setAudioUsage(any(), any()) } returns Unit
    every { PlaybackController.pause(any()) } returns Unit
    every { PlaybackController.playPreset(any(), any()) } returns Unit
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testWithoutPresetID() {
    val scenario = ActivityScenario.launch(AlarmRingerActivity::class.java)
    assertEquals(Lifecycle.State.DESTROYED, scenario.state)
  }

  @Test
  fun testWithPresetID() {
    val presetID = "test-preset-id"

    // cannot launch Activity using `ActivityScenario.launch(Intent)` method. For whatever reasons,
    // it increases the startup time for all subsequent `ActivityScenario.launch(Class)`s from
    // other classes.
    FragmentScenario.launch(Fragment::class.java).onFragment {
      it.startActivity(
        Intent(it.requireContext(), AlarmRingerActivity::class.java)
          .putExtra(AlarmRingerActivity.EXTRA_PRESET_ID, presetID)
      )
    }

    EspressoX.retryWithWaitOnError(AssertionError::class, wait = 1000L) {
      verify(exactly = 1) {
        PlaybackController.setAudioUsage(any(), AudioAttributesCompat.USAGE_ALARM)
        PlaybackController.playPreset(any(), presetID)
      }
    }

    clearMocks(PlaybackController)
    onView(withId(R.id.dismiss_slider)).perform(swipeRight())

    EspressoX.retryWithWaitOnError(AssertionError::class, wait = 1000L) {
      verify(exactly = 1) {
        PlaybackController.setAudioUsage(any(), AudioAttributesCompat.USAGE_MEDIA)
        PlaybackController.pause(any())
      }
    }
  }
}
