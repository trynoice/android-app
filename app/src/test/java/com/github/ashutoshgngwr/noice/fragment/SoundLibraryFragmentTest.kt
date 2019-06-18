package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.SeekBar
import androidx.annotation.IdRes
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.SoundManager
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class SoundLibraryFragmentTest {

  companion object {
    fun clickOn(@IdRes buttonId: Int): ViewAction {
      return object : ViewAction {
        override fun getDescription() = "Click on view with specified id"

        override fun getConstraints() = null

        override fun perform(uiController: UiController?, view: View?) {
          view!!.findViewById<View>(buttonId)!!.performClick()
        }
      }
    }

    fun seekProgressOf(@IdRes seekBarId: Int, progress: Int): ViewAction {
      return object : ViewAction {
        override fun getDescription() = "Adjust seek bar progress"

        override fun getConstraints() = null

        override fun perform(uiController: UiController?, view: View?) {
          val seekBar = view!!.findViewById<SeekBar>(seekBarId)
          shadowOf(seekBar).onSeekBarChangeListener.onProgressChanged(seekBar, progress, true)
        }
      }
    }
  }

  private lateinit var mFragmentScenario: FragmentScenario<SoundLibraryFragment>
  private lateinit var mSoundManager: SoundManager

  @Before
  fun setup() {
    mFragmentScenario = launchFragmentInContainer<SoundLibraryFragment>(null, R.style.AppTheme, null)
      .onFragment { fragment ->
        // see https://github.com/robolectric/robolectric/issues/834
        // so fake bind service..?
        val binder = Robolectric.buildService(MediaPlayerService::class.java).create().get()
          .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

        fragment.mServiceConnection.onServiceConnected(null, binder)
        mSoundManager = binder.getSoundManager()
      }
  }

  @Test
  fun `should create without any errors`() {
    mFragmentScenario
      .moveToState(Lifecycle.State.CREATED)
      .onFragment { fragment ->
        assert(fragment.view != null)
      }
  }

  @Test
  fun `should play corresponding sound on clicking play button`() {
    // click play button on a sound
    onView(withId(R.id.list_sound)).perform(
      RecyclerViewActions.actionOnItemAtPosition<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
        1, clickOn(R.id.button_play)
      )
    )

    assert(shadowOf(mSoundManager.mSoundPool).wasResourcePlayed(LIBRARY[1].resId))
  }

  @Test
  fun `should stop corresponding sound on clicking stop button`() {
    // play a sound
    mSoundManager.play(LIBRARY[0].key)

    // assert that sound was played and then clear SoundPools played state
    assert(shadowOf(mSoundManager.mSoundPool).wasResourcePlayed(LIBRARY[0].resId))
    shadowOf(mSoundManager.mSoundPool).clearPlayed()

    // click the stop button
    onView(withId(R.id.list_sound)).perform(
      RecyclerViewActions.actionOnItemAtPosition<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
        0, clickOn(R.id.button_play)
      )
    )

    // assert that it wasn't played and it is stopped in sound manager
    assert(!mSoundManager.isPlaying(LIBRARY[0].key))
    assert(!shadowOf(mSoundManager.mSoundPool).wasResourcePlayed(LIBRARY[0].resId))
  }

  @Test
  fun `should adjust volume of corresponding sound on dragging seek bar`() {
    onView(withId(R.id.list_sound)).perform(
      RecyclerViewActions.actionOnItemAtPosition<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
        3, seekProgressOf(R.id.seekbar_volume, 10)
      )
    )

    assert(mSoundManager.getVolume(LIBRARY[3].key) == 10)
  }

  @Test
  fun `should adjust time period of corresponding sound on dragging seek bar`() {
    onView(withId(R.id.list_sound)).perform(
      RecyclerViewActions.actionOnItemAtPosition<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
        3, seekProgressOf(R.id.seekbar_time_period, 145)
      )
    )

    assert(mSoundManager.getTimePeriod(LIBRARY[3].key) == 145)
  }

  @Test
  fun `should show snackBar message if a sound is played when playback is paused`() {
    // play a sound
    mSoundManager.play(LIBRARY[0].key)

    // pause playback, duh!
    mSoundManager.pausePlayback()

    // play another sound
    onView(withId(R.id.list_sound)).perform(
      RecyclerViewActions.actionOnItemAtPosition<SoundLibraryFragment.SoundListAdapter.ViewHolder>(
        0, clickOn(R.id.button_play)
      )
    )

    onView(withId(com.google.android.material.R.id.snackbar_text))
      .check(matches(withText(R.string.playback_is_paused)))
  }

  @Test
  fun `should display save preset button only if playback is on`() {
    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.GONE)))

    // play a sound
    mSoundManager.play(LIBRARY[0].key)

    // save preset should become visible
    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
  }

  @Test
  fun `should show save preset dialog on clicking save preset button`() {
    // play a sound
    mSoundManager.play(LIBRARY[0].key)

    onView(withId(R.id.fab_save_preset)).perform(click())
    val dialog = ShadowDialog.getShownDialogs()[0]
    assert(dialog.isShowing && dialog.findViewById<View>(R.id.layout_edit_name) != null)
  }

  @Test
  fun `should show snackBar with preset saved message on successful save`() {
    onView(withId(com.google.android.material.R.id.snackbar_text))
      .check(doesNotExist())

    mFragmentScenario.onFragment { fragment ->
      fragment.onActivityResult(SoundLibraryFragment.RC_SAVE_PRESET_DIALOG, Activity.RESULT_OK, null)
      onView(withId(com.google.android.material.R.id.snackbar_text))
        .check(matches(withText(R.string.preset_saved)))
    }
  }

  @Test
  fun `should destroy nice and quiet`() {
    mFragmentScenario.moveToState(Lifecycle.State.DESTROYED)
  }
}
