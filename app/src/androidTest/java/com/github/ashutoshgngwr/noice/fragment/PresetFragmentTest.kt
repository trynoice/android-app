package com.github.ashutoshgngwr.noice.fragment

import android.widget.Button
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.ViewActionsX
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import io.mockk.*
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.impl.annotations.RelaxedMockK
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.instanceOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  @RelaxedMockK
  private lateinit var eventBus: EventBus

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var fragment: PresetFragment

  private lateinit var fragmentScenario: FragmentScenario<PresetFragment>

  private val expectedPlayerStates = arrayOf(
    Preset.PlayerState(
      "test-1", Player.DEFAULT_VOLUME, Player.DEFAULT_TIME_PERIOD
    ),
    Preset.PlayerState(
      "test-2", Player.MAX_VOLUME, Player.MAX_TIME_PERIOD
    )
  )

  // returned when Preset.readAllFromUserPreferences() is called
  private lateinit var mockPreset: Preset

  @Before
  fun setup() {
    mockPreset = mockk(relaxed = true) {
      every { name } returns "test"
      every { playerStates } returns expectedPlayerStates
    }

    mockkObject(Preset.Companion)
    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf(mockPreset)
    fragmentScenario = launchFragmentInContainer<PresetFragment>(null, R.style.Theme_App)
    fragmentScenario.onFragment {
      fragment = it
      it.onPlayerManagerUpdate(mockk(relaxed = true))
    }

    MockKAnnotations.init(this)
  }

  @Test
  fun testInitialLayout() {
    onView(withId(R.id.indicator_list_empty))
      .check(matches(withEffectiveVisibility(Visibility.GONE)))

    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf()
    fragmentScenario.recreate()
    onView(withId(R.id.indicator_list_empty))
      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    // clear all previous calls to EventBus
    clearMocks(eventBus)
    onView(withId(R.id.list_presets)).perform(
      RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        ViewActionsX.clickOn(R.id.button_play)
      )
    )

    val eventSlots = mutableListOf<MediaPlayerService.StartPlayerEvent>()
    verifySequence {
      eventBus.unregister(any())
      eventBus.post(ofType(MediaPlayerService.StopPlaybackEvent::class))
      for (i in expectedPlayerStates.indices) {
        eventBus.post(capture(eventSlots))
      }

      eventBus.register(any())
    }

    for (i in expectedPlayerStates.indices) {
      assertEquals(expectedPlayerStates[i].soundKey, eventSlots[i].soundKey)
      assertEquals(expectedPlayerStates[i].timePeriod, eventSlots[i].timePeriod)
      assertEquals(expectedPlayerStates[i].volume, eventSlots[i].volume)
    }
  }

  @Test
  fun testRecyclerViewItem_stopButton() {
    // ensure that PresetFragment assumes it is playing a preset
    every { Preset.from(any(), any()) } returns mockPreset
    fragmentScenario.onFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
      })
    }

    onView(withId(R.id.list_presets)).perform(
      RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        ViewActionsX.clickOn(R.id.button_play)
      )
    )

    verifySequence {
      eventBus.unregister(any())
      eventBus.post(ofType(MediaPlayerService.StopPlaybackEvent::class))
      eventBus.register(any())
    }
  }

  @Test
  fun testRecyclerViewItem_deleteOption() {
    // open context menu
    onView(withId(R.id.list_presets)).perform(
      RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        ViewActionsX.clickOn(R.id.button_menu)
      )
    )

    onView(withText(R.string.delete)).perform(click()) // select delete option
    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
      .perform(click()) // click delete button in confirmation dialog

    onView(withText("test")).check(doesNotExist())
    verify(exactly = 1) {
      Preset.writeAllToUserPreferences(any(), emptyList())
    }
  }

  @Test
  fun testRecyclerViewItem_deleteOption_onPresetPlaying() {
    // ensure that PresetFragment assumes it is playing a preset
    every { Preset.from(any(), any()) } returns mockPreset
    fragmentScenario.onFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
      })
    }

    // open context menu
    onView(withId(R.id.list_presets)).perform(
      RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        ViewActionsX.clickOn(R.id.button_menu)
      )
    )

    onView(withText(R.string.delete)).perform(click()) // select delete option
    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
      .perform(click()) // click delete button in confirmation dialog

    // should publish a stop playback event if preset was playing
    verify(exactly = 1) { eventBus.post(ofType(MediaPlayerService.StopPlaybackEvent::class)) }
  }

  @Test
  fun testRecyclerViewItem_renameOption() {
    // open context menu
    onView(withId(R.id.list_presets)).perform(
      RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        ViewActionsX.clickOn(R.id.button_menu)
      )
    )

    onView(withText(R.string.rename)).perform(click()) // select rename option
    onView(withId(R.id.editText))
      .check(matches(isDisplayed())) // check if the rename dialog was displayed
      .perform(replaceText("test-renamed")) // replace text in input field

    onView(allOf(instanceOf(Button::class.java), withText(R.string.save)))
      .perform(click()) // click on positive button

    val presetsSlot = slot<List<Preset>>()
    verify(exactly = 1) {
      mockPreset.name = "test-renamed"
      Preset.writeAllToUserPreferences(any(), capture(presetsSlot))
    }

    assertEquals(mockPreset, presetsSlot.captured[0])
  }
}
