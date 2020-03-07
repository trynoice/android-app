package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.media.AudioAttributesCompat
import androidx.preference.PreferenceManager
import androidx.test.annotation.UiThreadTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class PresetFragmentTest {

  @Mock
  private lateinit var eventBus: EventBus

  @InjectMocks
  private lateinit var fragment: PresetFragment

  private lateinit var fragmentScenario: FragmentScenario<PresetFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInContainer<PresetFragment>(null, R.style.AppTheme)
      .onFragment { fragment = it }

    MockitoAnnotations.initMocks(this)
  }

  @After
  fun teardown() {
    if (::fragmentScenario.isInitialized) {
      fragmentScenario.onFragment {
        // clear any preferences saved by the tests
        PreferenceManager.getDefaultSharedPreferences(it.requireContext())
          .edit().clear().commit()
      }
    }
  }

  @Test
  fun testInitialLayout_withNoSavedPresets() {
    onView(withId(R.id.indicator_list_empty)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
  }

  @Test
  fun testInitialLayout_withSavedPresets() {
    fragmentScenario.onFragment {
      val playbacks = hashMapOf(
        "birds" to Playback(
          it.requireContext(),
          requireNotNull(Sound.LIBRARY["birds"]),
          123,
          AudioAttributesCompat.Builder().build()
        )
      )

      // save preset to user preferences
      val preset = PresetFragment.Preset("test", playbacks.values.toTypedArray())
      PresetFragment.Preset.appendToUserPreferences(it.requireContext(), preset)
    }

    fragmentScenario.recreate()

    onView(withId(R.id.indicator_list_empty)).check(matches(withEffectiveVisibility(Visibility.GONE)))
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    var playback: Playback? = null
    fragmentScenario.onFragment {
      playback = Playback(
        it.requireContext(),
        requireNotNull(Sound.LIBRARY["birds"]),
        123,
        AudioAttributesCompat.Builder().build()
      )

      // save preset to user preferences
      val preset = PresetFragment.Preset("test", arrayOf(requireNotNull(playback)))
      (requireNotNull(it.mRecyclerView).adapter as PresetFragment.PresetListAdapter)
        .dataSet.add(preset)
      requireNotNull(requireNotNull(it.mRecyclerView).adapter).notifyDataSetChanged()
    }

    onView(withId(R.id.button_play)).perform(click())

    verify(eventBus, atMost(3)).post(any())
    verify(eventBus).post(PlaybackControlEvents.StopPlaybackEvent())
    verify(eventBus).post(PlaybackControlEvents.StartPlaybackEvent("birds"))
    verify(eventBus).post(PlaybackControlEvents.UpdatePlaybackEvent(requireNotNull(playback)))
  }

  @Test
  fun testRecyclerViewItem_stopButton() {
    fragmentScenario.onFragment {
      val playback = Playback(
        it.requireContext(),
        requireNotNull(Sound.LIBRARY["birds"]),
        123,
        AudioAttributesCompat.Builder().build()
      )

      // save preset to user preferences
      val preset = PresetFragment.Preset("test", arrayOf(playback))
      (requireNotNull(it.mRecyclerView).adapter as PresetFragment.PresetListAdapter)
        .dataSet.add(preset)
      requireNotNull(requireNotNull(it.mRecyclerView).adapter).notifyDataSetChanged()
      it.onPlaybackUpdate(hashMapOf("birds" to playback))
    }

    onView(withId(R.id.button_play)).perform(click())
    verify(eventBus, atMostOnce()).post(any())
    verify(eventBus).post(PlaybackControlEvents.StopPlaybackEvent())
  }

  @Test
  fun testRecyclerViewItem_deleteButton() {
    fragmentScenario.onFragment {
      val playback = Playback(
        it.requireContext(),
        requireNotNull(Sound.LIBRARY["birds"]),
        123,
        AudioAttributesCompat.Builder().build()
      )

      // save preset to user preferences
      val preset = PresetFragment.Preset("test", arrayOf(playback))
      (requireNotNull(it.mRecyclerView).adapter as PresetFragment.PresetListAdapter)
        .dataSet.add(preset)
      requireNotNull(requireNotNull(it.mRecyclerView).adapter).notifyDataSetChanged()
      it.onPlaybackUpdate(hashMapOf("birds" to playback))
    }

    onView(withId(R.id.button_delete)).perform(click())
    onView(withText(R.string.delete)).perform(click())

    onView(withId(R.id.button_delete)).check(doesNotExist())
    verify(eventBus, atMostOnce()).post(any())
    verify(eventBus).post(PlaybackControlEvents.StopPlaybackEvent())
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
        ctx, requireNotNull(Sound.LIBRARY["birds"]), 123, AudioAttributesCompat.Builder().build()
      )
      // save preset to user preferences
      val preset = PresetFragment.Preset("test", arrayOf(playback))
      PresetFragment.Preset.appendToUserPreferences(ctx, preset)
      assertEquals(playback, PresetFragment.Preset.readAllFromUserPreferences(ctx)[0].playbacks[0])
    }
  }
}
