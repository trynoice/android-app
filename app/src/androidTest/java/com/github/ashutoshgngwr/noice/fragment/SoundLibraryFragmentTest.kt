package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
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
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class SoundLibraryFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  @RelaxedMockK
  private lateinit var eventBus: EventBus

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var fragment: SoundLibraryFragment
  private lateinit var fragmentScenario: FragmentScenario<SoundLibraryFragment>

  @Before
  fun setup() {
    mockkObject(InAppReviewFlowManager)
    fragmentScenario = launchFragmentInContainer<SoundLibraryFragment>(null, R.style.Theme_App)
    fragmentScenario.onFragment { fragment = it } // just for mock injection
    MockKAnnotations.init(this)
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    onView(withId(R.id.sound_list)).perform(
      RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    verify(exactly = 1) { eventBus.post(MediaPlayerService.StartPlayerEvent("birds")) }
    confirmVerified(eventBus)
  }

  @Test
  fun testRecyclerViewItem_stopButton() {
    val mockUpdateEvent = mockk<MediaPlayerService.OnPlayerManagerUpdateEvent>(relaxed = true) {
      every { players } returns hashMapOf("birds" to mockk(relaxed = true))
    }

    fragmentScenario.onFragment { it.onPlayerManagerUpdate(mockUpdateEvent) }
    onView(withId(R.id.sound_list)).perform(
      RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    verify(exactly = 1) { eventBus.post(MediaPlayerService.StopPlayerEvent("birds")) }
  }

  @Test
  fun testRecyclerViewItem_volumeSlider() {
    val mockPlayer = mockk<Player>(relaxed = true) {
      every { volume } returns Player.DEFAULT_VOLUME
    }

    val mockUpdateEvent = mockk<MediaPlayerService.OnPlayerManagerUpdateEvent>(relaxed = true) {
      every { players } returns hashMapOf("birds" to mockPlayer)
    }

    every {
      eventBus.getStickyEvent(MediaPlayerService.OnPlayerManagerUpdateEvent::class.java)
    } returns mockUpdateEvent

    fragmentScenario.onFragment { it.onPlayerManagerUpdate(mockUpdateEvent) }
    val expectedVolumes = arrayOf(0, Player.MAX_VOLUME, Random.nextInt(1, Player.MAX_VOLUME))
    for (expectedVolume in expectedVolumes) {
      onView(withId(R.id.sound_list)).perform(
        RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
          EspressoX.slideInItem(R.id.volume_slider, expectedVolume.toFloat())
        )
      )

      verify(exactly = 1) { mockPlayer.setVolume(expectedVolume) }
    }
  }

  @Test
  fun testRecyclerViewItem_timePeriodSlider() {
    val mockPlayer = mockk<Player>(relaxed = true) {
      every { timePeriod } returns Player.DEFAULT_TIME_PERIOD
    }

    val mockUpdateEvent = mockk<MediaPlayerService.OnPlayerManagerUpdateEvent>(relaxed = true) {
      every { players } returns hashMapOf("rolling_thunder" to mockPlayer)
    }

    every {
      eventBus.getStickyEvent(MediaPlayerService.OnPlayerManagerUpdateEvent::class.java)
    } returns mockUpdateEvent

    fragmentScenario.onFragment { it.onPlayerManagerUpdate(mockUpdateEvent) }
    val expectedTimePeriods = arrayOf(
      Player.MIN_TIME_PERIOD,
      Player.MAX_TIME_PERIOD,
      // following because step size of the slider is 10s
      Random.nextInt(Player.MIN_TIME_PERIOD / 10, Player.MAX_TIME_PERIOD / 10) * 10
    )

    for (expectedTimePeriod in expectedTimePeriods) {
      onView(withId(R.id.sound_list))
        .perform(
          RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
            hasDescendant(allOf(withId(R.id.title), withText(R.string.rolling_thunder)))
          ),
          RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
            hasDescendant(allOf(withId(R.id.title), withText(R.string.rolling_thunder))),
            EspressoX.slideInItem(R.id.time_period_slider, expectedTimePeriod.toFloat())
          )
        )

      verify(exactly = 1) { mockPlayer.timePeriod = expectedTimePeriod }
    }
  }

  @Test
  fun testSavePresetButton_onUnknownPresetPlayback() {
    val mockUpdateEvent = mockk<MediaPlayerService.OnPlayerManagerUpdateEvent>(relaxed = true) {
      every { players } returns mockk(relaxed = true)
    }

    for (state in PlayerManager.State.values()) {
      every { mockUpdateEvent.state } returns state
      fragmentScenario.onFragment {
        it.onPlayerManagerUpdate(mockUpdateEvent)
      }

      // a non-playing state should keep the FAB hidden
      var expectedVisibility = Visibility.GONE
      if (state == PlayerManager.State.PLAYING) {
        expectedVisibility = Visibility.VISIBLE
      }

      onView(withId(R.id.save_preset_button))
        .check(matches(withEffectiveVisibility(expectedVisibility)))
    }
  }

  @Test
  fun testSavePresetButton_onKnownPresetPlayback() {
    val mockPlayers: Map<String, Player> = hashMapOf(
      "birds" to mockk(relaxed = true),
      "rolling_thunder" to mockk(relaxed = true)
    )

    val preset = Preset.from("test", mockPlayers.values)
    mockkObject(Preset.Companion)
    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf(preset)
    every { Preset.from("", mockPlayers.values) } returns preset
    fragmentScenario.onFragment { fragment ->
      fragment.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
        every { players } returns mockPlayers
      })
    }

    onView(withId(R.id.save_preset_button))
      .check(matches(withEffectiveVisibility(Visibility.GONE)))

    // when volume/time period slider is adjusted, save preset button should be visible.
    // since the Sticky Event is mocked, the item being slided will perform a mock call. So we'll
    // need to return a different instance of events (with expected value) when other components
    // request it after sliding action happens.
    every {
      eventBus.getStickyEvent(MediaPlayerService.OnPlayerManagerUpdateEvent::class.java)
    } returns mockk(relaxed = true) {
      every { state } returns PlayerManager.State.PLAYING
      every { players } returns hashMapOf(
        "birds" to mockk(relaxed = true) {
          every { volume } returns 6
        },
        "rolling_thunder" to mockk(relaxed = true)
      )
    }

    onView(withId(R.id.sound_list)).perform(
      RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
        EspressoX.slideInItem(R.id.volume_slider, 6f)
      )
    )

    verify(exactly = 1) { requireNotNull(mockPlayers["birds"]).setVolume(6) }
    onView(withId(R.id.save_preset_button))
      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
  }

  @Test
  fun testSavePresetButton_onClick() {
    val mockPlayers: Map<String, Player> = hashMapOf(
      "birds" to mockk(relaxed = true) {
        every { soundKey } returns "birds"
        every { volume } returns 1
        every { timePeriod } returns Player.MIN_TIME_PERIOD + 2
      },
      "rolling_thunder" to mockk(relaxed = true) {
        every { soundKey } returns "rolling_thunder"
        every { volume } returns 3
        every { timePeriod } returns Player.MIN_TIME_PERIOD + 4
      }
    )

    val preset = Preset.from("test", mockPlayers.values)
    val mockValidator = mockk<(String) -> Boolean>()
    mockkObject(Preset.Companion)
    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf()
    every { Preset.from("", mockPlayers.values) } returns preset
    every { Preset.duplicateNameValidator(any()) } returns mockValidator
    every { mockValidator.invoke("test-exists") } returns true
    every { mockValidator.invoke("test-does-not-exists") } returns false

    fragmentScenario.onFragment { fragment ->
      fragment.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
        every { players } returns mockPlayers
      })
    }

    onView(withId(R.id.save_preset_button))
      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
      .perform(click())

    onView(allOf(withId(R.id.title), withText(R.string.save_preset)))
      .check(matches(isDisplayed()))

    // should disable save button for duplicate names
    onView(withId(R.id.editText))
      .check(matches(isDisplayed()))
      .perform(replaceText("test-exists"))

    onView(allOf(withId(R.id.positive), withText(R.string.save)))
      .check(matches(not(isEnabled())))

    onView(withId(R.id.editText))
      .check(matches(isDisplayed()))
      .perform(replaceText("test-does-not-exists"))

    onView(allOf(withId(R.id.positive), withText(R.string.save)))
      .check(matches(isEnabled()))
      .perform(click())

    onView(withId(R.id.save_preset_button)).check(matches(not(isDisplayed())))
    onView(withId(com.google.android.material.R.id.snackbar_text))
      .check(matches(withText(R.string.preset_saved)))

    val presetSlot = slot<Preset>()
    verify { Preset.appendToUserPreferences(any(), capture(presetSlot)) }

    for (playerState in presetSlot.captured.playerStates) {
      assertEquals(mockPlayers[playerState.soundKey]?.volume, playerState.volume)
      assertEquals(mockPlayers[playerState.soundKey]?.timePeriod, playerState.timePeriod)
    }

    verify(exactly = 1) { InAppReviewFlowManager.maybeAskForReview(any()) }
  }

  @Test
  fun testRandomPresetButton_onPlayback() {
    val mockUpdateEvent = mockk<MediaPlayerService.OnPlayerManagerUpdateEvent>(relaxed = true) {
      every { players } returns mockk(relaxed = true)
    }

    for (state in PlayerManager.State.values()) {
      every { mockUpdateEvent.state } returns state
      fragmentScenario.onFragment {
        it.onPlayerManagerUpdate(mockUpdateEvent)
      }

      // a non-stopped state should keep the FAB hidden
      var expectedVisibility = Visibility.GONE
      if (state == PlayerManager.State.STOPPED) {
        expectedVisibility = Visibility.VISIBLE
      }

      onView(withId(R.id.random_preset_button))
        .check(matches(withEffectiveVisibility(expectedVisibility)))
    }
  }

  @Test
  fun testRandomPresetButton_onClick() {
    val intensityExpectations = mapOf(
      R.id.preset_intensity__any to SoundLibraryFragment.RANGE_INTENSITY_ANY,
      R.id.preset_intensity__dense to SoundLibraryFragment.RANGE_INTENSITY_DENSE,
      R.id.preset_intensity__light to SoundLibraryFragment.RANGE_INTENSITY_LIGHT
    )

    val typeExpectations = mapOf(
      R.id.preset_type__any to null,
      R.id.preset_type__focus to Sound.Tag.FOCUS,
      R.id.preset_type__relax to Sound.Tag.RELAX
    )

    for ((typeID, tag) in typeExpectations) {
      for ((intensityID, intensityRange) in intensityExpectations) {
        onView(withId(R.id.random_preset_button))
          .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
          .perform(click())

        onView(allOf(withId(R.id.title), withText(R.string.random_preset)))
          .check(matches(isDisplayed()))
          // swipeUp to reveal bottom sheet completely; click to release the tap from swipeUp..?!!
          // first click after swipeUp wasn't working otherwise.
          .perform(swipeUp(), click())

        onView(withId(typeID)).perform(click())
        onView(withId(intensityID)).perform(click())
        onView(allOf(withId(R.id.positive), withText(R.string.play)))
          .perform(click())

        val eventSlot = slot<MediaPlayerService.PlayPresetEvent>()
        verify(exactly = 1) { eventBus.post(capture(eventSlot)) }
        assertTrue("should capture a PlayPresetEvent", eventSlot.isCaptured)

        val preset = eventSlot.captured.preset
        assertTrue(
          "should have expected intensity",
          preset.playerStates.size in intensityRange
        )

        preset.playerStates.forEach {
          assertTrue(
            "should have expected sound tags",
            tag == null || Sound.get(it.soundKey).tags.contains(tag)
          )
        }

        clearMocks(eventBus)
      }
    }
  }
}
