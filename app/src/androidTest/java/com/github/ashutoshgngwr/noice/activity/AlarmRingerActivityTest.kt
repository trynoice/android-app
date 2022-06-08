package com.github.ashutoshgngwr.noice.activity

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.media.AudioAttributesCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AlarmRingerActivityTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var spyPlaybackController: PlaybackController

  @Before
  fun setup() {
    // since alarm activity ringer has an hack to start media player service in the foreground, the
    // tests crash complaining `Context.startForegroundService() did not then call
    // Service.startForeground()` if the PlaybackController is mocked. To work around the error, use
    // a spy instance and manually stop the service when tearing down the test.
    val context = ApplicationProvider.getApplicationContext<Context>()
    spyPlaybackController = spyk(PlaybackController(context, mockk(relaxed = true)))
  }

  @After
  fun tearDown() {
    spyPlaybackController.stop()
  }

  @Test
  fun testWithoutPresetID() {
    val scenario = ActivityScenario.launch(AlarmRingerActivity::class.java)
    verify(exactly = 1, timeout = 5000L) { spyPlaybackController.pause() }
    assertEquals(Lifecycle.State.DESTROYED, scenario.state)
  }

  @Test
  fun testWithPresetID() {
    val presetID = "test-preset-id"

    // cannot launch Activity using `ActivityScenario.launch(Intent)` method. For whatever reasons,
    // it increases the startup time for all subsequent `ActivityScenario.launch(Class)`s from
    // other classes.
    launchFragmentInHiltContainer<Fragment>().onFragment {
      it.startActivity(
        Intent(it.requireContext(), AlarmRingerActivity::class.java)
          .putExtra(AlarmRingerActivity.EXTRA_PRESET_ID, presetID)
      )
    }

    onView(withId(R.id.dismiss_slider)).check(matches(isDisplayed()))
    verify(exactly = 1, timeout = 5000L) {
      spyPlaybackController.setAudioUsage(AudioAttributesCompat.USAGE_ALARM)
      spyPlaybackController.playPreset(presetID)
    }

    onView(withId(R.id.dismiss_slider)).perform(swipeRight())
    verify(exactly = 1, timeout = 5000L) {
      spyPlaybackController.setAudioUsage(AudioAttributesCompat.USAGE_MEDIA)
      spyPlaybackController.pause()
    }
  }
}
