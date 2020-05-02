package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.widget.Button
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.media.AudioAttributesCompat
import androidx.preference.PreferenceManager
import androidx.test.annotation.UiThreadTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Playback
import com.github.ashutoshgngwr.noice.sound.PlaybackControlEvents
import com.github.ashutoshgngwr.noice.sound.Sound
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.atMostOnce
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class PresetFragmentTest {

  @Mock
  private lateinit var eventBus: EventBus

  @InjectMocks
  private lateinit var fragment: PresetFragment

  private lateinit var preset: PresetFragment.Preset
  private lateinit var fragmentScenario: FragmentScenario<PresetFragment>

  private fun getContext(): Context {
    return InstrumentationRegistry.getInstrumentation().targetContext
  }

  @Before
  fun setup() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      preset = PresetFragment.Preset(
        "test", arrayOf(
          Playback(
            getContext(),
            requireNotNull(Sound.LIBRARY["birds"]),
            AudioAttributesCompat.Builder().build()
          )
        )
      )

      PresetFragment.Preset.appendToUserPreferences(getContext(), preset)
    }

    fragmentScenario = launchFragmentInContainer<PresetFragment>(null, R.style.Theme_App)
    fragmentScenario.onFragment { fragment = it }
    MockitoAnnotations.initMocks(this)
  }

  @After
  fun teardown() {
    PreferenceManager.getDefaultSharedPreferences(getContext())
      .edit().clear().commit()
  }

  @Test
  fun testInitialLayout() {
    onView(withId(R.id.indicator_list_empty))
      .check(matches(withEffectiveVisibility(Visibility.GONE)))

    PreferenceManager.getDefaultSharedPreferences(getContext())
      .edit().clear().commit()

    fragmentScenario.recreate()
    onView(withId(R.id.indicator_list_empty))
      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    onView(withId(R.id.button_play)).perform(click())

    val expectedPlayback = preset.playbackStates[0]
    verify(eventBus, atMostOnce()).post(PlaybackControlEvents.StopPlaybackEvent())
    verify(eventBus, atMostOnce()).post(PlaybackControlEvents.UpdatePlaybackEvent(expectedPlayback))
    verify(eventBus, atMostOnce())
      .post(PlaybackControlEvents.StartPlaybackEvent(expectedPlayback.soundKey))
  }

  @Test
  fun testRecyclerViewItem_stopButton() {
    fragmentScenario.onFragment {
      it.onPlaybackUpdate(hashMapOf<String, Playback>().also { playbacks ->
        preset.playbackStates.forEach { p -> playbacks[p.soundKey] = p }
      })
    }

    onView(withId(R.id.button_play)).perform(click())
    verify(eventBus, atMostOnce()).post(PlaybackControlEvents.StopPlaybackEvent())
  }

  @Test
  fun testRecyclerViewItem_deleteOption() {
    onView(withId(R.id.button_menu)).perform(click()) // open context menu
    onView(withText(R.string.delete)).perform(click()) // select delete option
    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
      .perform(click()) // click delete button in confirmation dialog

    onView(withText(preset.name)).check(doesNotExist())
    assertEquals(0, PresetFragment.Preset.readAllFromUserPreferences(getContext()).size)
  }

  @Test
  fun testRecyclerViewItem_deleteOption_onPresetPlaying() {
    // pretend that the stuff is playing
    fragmentScenario.onFragment {
      it.onPlaybackUpdate(hashMapOf<String, Playback>().also { playbacks ->
        preset.playbackStates.forEach { p -> playbacks[p.soundKey] = p }
      })
    }

    onView(withId(R.id.button_menu)).perform(click()) // open context menu
    onView(withText(R.string.delete)).perform(click()) // select delete option
    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
      .perform(click()) // click delete button in confirmation dialog

    // should publish a stop playback event if preset was playing
    verify(eventBus, atMostOnce()).post(PlaybackControlEvents.StopPlaybackEvent())
  }

  @Test
  fun testRecyclerViewItem_renameOption() {
    onView(withId(R.id.button_menu)).perform(click()) // open context menu
    onView(withText(R.string.rename)).perform(click()) // select rename option
    onView(withId(R.id.editText))
      .check(matches(isDisplayed())) // check if the rename dialog was displayed
      .perform(replaceText("test-renamed")) // replace text in input field

    onView(allOf(instanceOf(Button::class.java), withText(R.string.save)))
      .perform(click()) // click on positive button

    PresetFragment.Preset.readAllFromUserPreferences(getContext()).let {
      assertEquals(1, it.size)
      assertEquals("test-renamed", it[0].name)
    }
  }

  @RunWith(AndroidJUnit4::class)
  class PresetTest {

    @After
    fun teardown() {
      PreferenceManager
        .getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        .edit()
        .clear()
        .commit()
    }

    @Test
    @UiThreadTest
    fun testSavePresets() {
      val ctx = InstrumentationRegistry.getInstrumentation().targetContext
      val playback = Playback(
        ctx, requireNotNull(Sound.LIBRARY["birds"]), AudioAttributesCompat.Builder().build()
      )
      // save preset to user preferences
      val preset = PresetFragment.Preset("test", arrayOf(playback))
      PresetFragment.Preset.appendToUserPreferences(ctx, preset)
      assertEquals(
        playback,
        PresetFragment.Preset.readAllFromUserPreferences(ctx)[0].playbackStates[0]
      )
    }
  }
}
