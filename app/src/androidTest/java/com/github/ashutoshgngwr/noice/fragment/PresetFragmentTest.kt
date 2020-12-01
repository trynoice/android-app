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
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matchers.*
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

  // returned when Preset.readAllFromUserPreferences() is called
  private lateinit var mockPreset: Preset

  @Before
  fun setup() {
    mockPreset = mockk(relaxed = true) {
      every { name } returns "test"
      every { playerStates } returns arrayOf(
        Preset.PlayerState("test-1", Player.DEFAULT_VOLUME, Player.DEFAULT_TIME_PERIOD),
        Preset.PlayerState("test-2", Player.MAX_VOLUME, Player.MAX_TIME_PERIOD)
      )
    }

    mockkObject(InAppReviewFlowManager)
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
    onView(withId(R.id.empty_list_hint))
      .check(matches(withEffectiveVisibility(Visibility.GONE)))

    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf()
    fragmentScenario.recreate()
    onView(withId(R.id.empty_list_hint))
      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    // clear all previous calls to EventBus
    clearMocks(eventBus)
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    val eventSlot = slot<MediaPlayerService.PlayPresetEvent>()
    verify(exactly = 1) { eventBus.post(capture(eventSlot)) }
    assertEquals(mockPreset, eventSlot.captured.preset)
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

    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    verify(exactly = 1) { eventBus.post(ofType(MediaPlayerService.StopPlaybackEvent::class)) }
  }

  @Test
  fun testRecyclerViewItem_deleteOption() {
    // open context menu
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.menu_button)
      )
    )

    onView(withText(R.string.delete)).perform(click()) // select delete option
    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
      .perform(click()) // click delete button in confirmation dialog

    onView(withText("test")).check(doesNotExist())
    verify(exactly = 1) {
      Preset.writeAllToUserPreferences(any(), emptyList())
      InAppReviewFlowManager.maybeAskForReview(any())
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
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.menu_button)
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
    // if preset with given name already exists, save button should be disabled
    val mockValidator = mockk<(String) -> Boolean>()
    every { Preset.duplicateNameValidator(any()) } returns mockValidator
    every { mockValidator.invoke("test-exists") } returns true
    every { mockValidator.invoke("test-does-not-exists") } returns false

    // open context menu
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.menu_button)
      )
    )

    onView(withText(R.string.rename)).perform(click()) // select rename option
    onView(withId(R.id.editText))
      .check(matches(isDisplayed())) // check if the rename dialog was displayed
      .perform(replaceText("test-exists")) // replace text in input field

    onView(allOf(instanceOf(Button::class.java), withText(R.string.save)))
      .check(matches(not(isEnabled())))

    onView(withId(R.id.editText))
      .check(matches(isDisplayed()))
      .perform(replaceText("test-does-not-exists"))

    onView(allOf(instanceOf(Button::class.java), withText(R.string.save)))
      .check(matches(isEnabled()))
      .perform(click()) // click on positive button

    val presetsSlot = slot<List<Preset>>()
    verify(exactly = 1) {
      mockPreset.name = "test-does-not-exists"
      Preset.writeAllToUserPreferences(any(), capture(presetsSlot))
      InAppReviewFlowManager.maybeAskForReview(any())
    }

    assertEquals(mockPreset, presetsSlot.captured[0])
  }
}
