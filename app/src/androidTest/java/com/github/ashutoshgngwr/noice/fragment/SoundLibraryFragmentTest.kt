package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.view.View
import android.widget.SeekBar
import androidx.annotation.IdRes
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.media.AudioAttributesCompat
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Playback
import com.github.ashutoshgngwr.noice.sound.PlaybackControlEvents
import com.github.ashutoshgngwr.noice.sound.Sound
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.instanceOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class SoundLibraryFragmentTest {

  @Mock
  private lateinit var eventBus: EventBus

  @InjectMocks
  private lateinit var fragment: SoundLibraryFragment
  private lateinit var fragmentScenario: FragmentScenario<SoundLibraryFragment>


  private fun clickOn(@Suppress("SameParameterValue") @IdRes buttonId: Int): ViewAction {
    return object : ViewAction {
      override fun getDescription() = "Click on view with specified id"

      override fun getConstraints() = null

      override fun perform(uiController: UiController?, view: View?) {
        view!!.findViewById<View>(buttonId)!!.performClick()
      }
    }
  }

  private fun seekProgress(@IdRes seekBarId: Int, progress: Int): ViewAction {
    return object : ViewAction {
      override fun getDescription(): String {
        return "Emulate user input on a seek bar"
      }

      override fun getConstraints(): Matcher<View> {
        return instanceOf(SeekBar::class.java)
      }

      override fun perform(uiController: UiController?, view: View?) {
        requireNotNull(uiController)

        // TODO: following is a buggy implementation of emulating a touch input on a SeekBar.
        // The MotionEvent starts and ends at the incorrect positions.
        // To debug, turn on 'Show taps' and 'Pointer location' in developer options of the device.
        val seekBar = requireNotNull(view).findViewById<SeekBar>(seekBarId)
        val location = intArrayOf(0, 0)
        seekBar.getLocationOnScreen(location)
        val xOffset = location[0].toFloat() + seekBar.paddingStart
        val xStart = ((seekBar.progress.toFloat() / seekBar.max) * seekBar.width) + xOffset
        val x = ((progress.toFloat() / seekBar.max) * seekBar.width) + xOffset
        val y = location[1] + seekBar.paddingTop + (seekBar.height.toFloat() / 2)
        val startCoordinates = floatArrayOf(xStart, y)
        val endCoordinates = floatArrayOf(x, y)
        val precision = floatArrayOf(1f, 1f)

        // Send down event, pause, and send up
        val down = MotionEvents.sendDown(uiController, startCoordinates, precision).down
        uiController.loopMainThreadForAtLeast(100)
        MotionEvents.sendMovement(uiController, down, endCoordinates)
        uiController.loopMainThreadForAtLeast(100)
        MotionEvents.sendUp(uiController, down, endCoordinates)
      }
    }
  }

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInContainer<SoundLibraryFragment>(null, R.style.AppTheme, null)
    fragmentScenario.onFragment {
      fragment = it
      PreferenceManager.getDefaultSharedPreferences(it.requireContext()).edit().clear().commit()
    }

    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    onView(withId(R.id.list_sound))
      .perform(
        RecyclerViewActions.actionOnItem<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
          clickOn(R.id.button_play)
        )
      )

    verify(eventBus).post(PlaybackControlEvents.StartPlaybackEvent("birds"))
    reset(eventBus)

    onView(withId(R.id.list_sound))
      .perform(
        RecyclerViewActions.actionOnItem<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.train_horn))),
          clickOn(R.id.button_play)
        )
      )

    verify(eventBus).post(PlaybackControlEvents.StartPlaybackEvent("train_horn"))
  }

  @Test
  fun testRecyclerViewItem_stopButton() {
    // play april fools joke on the fragment with a fake playback.
    fragmentScenario.onFragment {
      it.onPlaybackUpdate(
        hashMapOf(
          "birds" to Playback(
            InstrumentationRegistry.getInstrumentation().targetContext,
            requireNotNull(Sound.LIBRARY["birds"]),
            123,
            AudioAttributesCompat.Builder().build()
          )
        )
      )
    }

    // stop the fake playback and see if it actually works.
    onView(withId(R.id.list_sound))
      .perform(
        RecyclerViewActions.actionOnItem<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
          clickOn(R.id.button_play)
        )
      )

    verify(eventBus).post(PlaybackControlEvents.StopPlaybackEvent("birds"))
  }

  @Test
  fun testRecyclerViewItem_volumeSeekBar() {
    var fakePlayback: Playback? = null
    fragmentScenario.onFragment {
      fakePlayback = Playback(
        InstrumentationRegistry.getInstrumentation().targetContext,
        requireNotNull(Sound.LIBRARY["birds"]),
        123,
        AudioAttributesCompat.Builder().build()
      )

      it.onPlaybackUpdate(hashMapOf("birds" to requireNotNull(fakePlayback)))
    }

    onView(withId(R.id.list_sound))
      .perform(
        RecyclerViewActions.actionOnItem<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
          seekProgress(R.id.seekbar_volume, Playback.MAX_VOLUME)
        )
      )

    verify(eventBus).post(PlaybackControlEvents.UpdatePlaybackEvent(requireNotNull(fakePlayback)))
    assertEquals(Playback.MAX_VOLUME, requireNotNull(fakePlayback).volume)
  }

  @Test
  fun testRecyclerViewItem_timePeriodSeekBar() {
    var fakePlayback: Playback? = null
    fragmentScenario.onFragment {
      fakePlayback = Playback(
        InstrumentationRegistry.getInstrumentation().targetContext,
        requireNotNull(Sound.LIBRARY["rolling_thunder"]),
        123,
        AudioAttributesCompat.Builder().build()
      )

      it.onPlaybackUpdate(hashMapOf("rolling_thunder" to requireNotNull(fakePlayback)))
    }

    // min time period should be 1 in any case
    onView(withId(R.id.list_sound))
      .perform(
        RecyclerViewActions.scrollTo<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.rolling_thunder)))
        ),
        RecyclerViewActions.actionOnItem<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.rolling_thunder))),
          seekProgress(R.id.seekbar_time_period, 0)
        )
      )

    assertEquals(1, requireNotNull(fakePlayback).timePeriod)
    verify(eventBus).post(PlaybackControlEvents.UpdatePlaybackEvent(requireNotNull(fakePlayback)))
    reset(eventBus)

    onView(withId(R.id.list_sound))
      .perform(
        RecyclerViewActions.actionOnItem<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.rolling_thunder))),
          seekProgress(R.id.seekbar_time_period, Playback.MAX_TIME_PERIOD)
        )
      )

    verify(eventBus).post(PlaybackControlEvents.UpdatePlaybackEvent(requireNotNull(fakePlayback)))
    assertEquals(Playback.MAX_TIME_PERIOD, requireNotNull(fakePlayback).timePeriod)
  }

  @Test
  fun testSavePresetButton_onUnknownPresetPlayback() {
    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.GONE)))

    // play a sound
    fragmentScenario.onFragment {
      it.onPlaybackUpdate(
        hashMapOf(
          "birds" to Playback(
            InstrumentationRegistry.getInstrumentation().targetContext,
            requireNotNull(Sound.LIBRARY["birds"]),
            123,
            AudioAttributesCompat.Builder().build()
          ),
          "rolling_thunder" to Playback(
            InstrumentationRegistry.getInstrumentation().targetContext,
            requireNotNull(Sound.LIBRARY["rolling_thunder"]),
            123,
            AudioAttributesCompat.Builder().build()
          )
        )
      )
    }

    // save preset should become visible
    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
  }

  @Test
  fun testSavePresetButton_onKnownPresetPlayback() {
    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.GONE)))

    // play a sound
    fragmentScenario.onFragment {
      val playbacks = hashMapOf(
        "birds" to Playback(
          InstrumentationRegistry.getInstrumentation().targetContext,
          requireNotNull(Sound.LIBRARY["birds"]),
          123,
          AudioAttributesCompat.Builder().build()
        ),
        "rolling_thunder" to Playback(
          InstrumentationRegistry.getInstrumentation().targetContext,
          requireNotNull(Sound.LIBRARY["rolling_thunder"]),
          123,
          AudioAttributesCompat.Builder().build()
        )
      )

      // save preset to user preferences
      val preset = PresetFragment.Preset("test", playbacks.values.toTypedArray())
      PresetFragment.Preset.appendToUserPreferences(it.requireContext(), preset)
      // deliver playback update
      it.onPlaybackUpdate(playbacks)
    }

    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.GONE)))
  }

  @Test
  fun testSavePresetButton_onClick() {
    fragmentScenario.onFragment {
      val playbacks = hashMapOf(
        "birds" to Playback(
          InstrumentationRegistry.getInstrumentation().targetContext,
          requireNotNull(Sound.LIBRARY["birds"]),
          123,
          AudioAttributesCompat.Builder().build()
        )
      )

      it.onPlaybackUpdate(playbacks)
    }

    onView(withId(R.id.fab_save_preset))
      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
      .perform(click())

    onView(allOf(withId(R.id.layout_main), hasDescendant(withId(R.id.edit_name))))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testSavePresetButton_onActivityResult() {
    onView(withId(com.google.android.material.R.id.snackbar_text))
      .check(doesNotExist())

    fragmentScenario.onFragment {
      it.onActivityResult(SoundLibraryFragment.RC_SAVE_PRESET_DIALOG, Activity.RESULT_OK, null)
    }

    onView(withId(com.google.android.material.R.id.snackbar_text))
      .check(matches(withText(R.string.preset_saved)))
  }
}
