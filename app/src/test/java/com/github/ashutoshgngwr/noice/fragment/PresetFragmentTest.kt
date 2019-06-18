package com.github.ashutoshgngwr.noice.fragment

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
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
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(AndroidJUnit4::class)
class PresetFragmentTest {

  private lateinit var mFragmentScenario: FragmentScenario<PresetFragment>
  private lateinit var mSoundManager: SoundManager

  @Before
  fun setup() {
    // save a new preset
    val preset = PresetFragment.Preset(
      "foo", arrayOf(PresetFragment.Preset.PresetPlaybackState(LIBRARY[0].key, 0.4f, 123))
    )

    PresetFragment.Preset.appendToUserPreferences(ApplicationProvider.getApplicationContext(), preset)

    mFragmentScenario = launchFragmentInContainer<PresetFragment>(null, R.style.AppTheme).onFragment { fragment ->
      // see https://github.com/robolectric/robolectric/issues/834
      // so fake bind service..? again..?
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
  fun `should display empty list indicator if no saved presets were found`() {
    onView(withId(R.id.button_delete)).perform(click())
    (ShadowAlertDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()

    onView(withId(R.id.indicator_list_empty)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
  }

  @Test
  fun `should not display empty list indicator if saved presets were found`() {
    onView(withId(R.id.indicator_list_empty)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))
  }

  @Test
  fun `should play preset on clicking play button`() {
    onView(withId(R.id.button_play)).perform(click())
    assert(mSoundManager.isPlaying && shadowOf(mSoundManager.mSoundPool).wasResourcePlayed(LIBRARY[0].resId))
  }

  @Test
  fun `should stop preset on clicking stop button`() {
    // play a preset
    onView(withId(R.id.button_play)).perform(click())
    assert(mSoundManager.isPlaying && shadowOf(mSoundManager.mSoundPool).wasResourcePlayed(LIBRARY[0].resId))

    shadowOf(mSoundManager.mSoundPool).clearPlayed()
    onView(withId(R.id.button_play)).perform(click())
    assert(!mSoundManager.isPlaying && !shadowOf(mSoundManager.mSoundPool).wasResourcePlayed(LIBRARY[0].resId))
  }

  @Test
  fun `should stop preset when it is deleted`() {
    // play a preset
    onView(withId(R.id.button_play)).perform(click())
    assert(mSoundManager.isPlaying && shadowOf(mSoundManager.mSoundPool).wasResourcePlayed(LIBRARY[0].resId))

    shadowOf(mSoundManager.mSoundPool).clearPlayed()
    onView(withId(R.id.button_delete)).perform(click())
    (ShadowAlertDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()

    assert(!mSoundManager.isPlaying && !shadowOf(mSoundManager.mSoundPool).wasResourcePlayed(LIBRARY[0].resId))
    // assert list is empty after the only item in it, is remove
    onView(withId(R.id.button_play)).check(doesNotExist())
  }

  @Test
  fun `should destroy nice and quiet`() {
    mFragmentScenario.moveToState(Lifecycle.State.DESTROYED)
  }

  @RunWith(AndroidJUnit4::class)
  class PresetTest {

    @Test
    fun `should be able to save presets user preferences`() {
      val preset = PresetFragment.Preset(
        "foo", arrayOf(PresetFragment.Preset.PresetPlaybackState(LIBRARY[0].key, 0.4f, 123))
      )

      PresetFragment.Preset.appendToUserPreferences(ApplicationProvider.getApplicationContext(), preset)
      assert(
        PresetFragment.Preset.readAllFromUserPreferences(ApplicationProvider.getApplicationContext())
          .contentEquals(arrayOf(preset))
      )
    }
  }
}
