package com.github.ashutoshgngwr.noice.fragment

import android.support.v4.media.session.PlaybackStateCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.playback.Player
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class LibraryFragmentTest {

  private lateinit var mockEventBus: EventBus
  private lateinit var mockPresetRepository: PresetRepository
  private lateinit var mockSettingsRepository: SettingsRepository
  private lateinit var mockReviewFlowProvider: ReviewFlowProvider
  private lateinit var fragmentScenario: FragmentScenario<LibraryFragment>

  @Before
  fun setup() {
    mockkStatic(EventBus::class)
    mockkObject(
      PlaybackController,
      PresetRepository.Companion,
      SettingsRepository.Companion
    )

    mockReviewFlowProvider = mockk(relaxed = true)
    ApplicationProvider.getApplicationContext<NoiceApplication>()
      .setReviewFlowProvider(mockReviewFlowProvider)

    mockEventBus = mockk(relaxed = true)
    mockPresetRepository = mockk(relaxed = true)
    mockSettingsRepository = mockk(relaxed = true)

    every { EventBus.getDefault() } returns mockEventBus
    every { PresetRepository.newInstance(any()) } returns mockPresetRepository
    every { SettingsRepository.newInstance(any()) } returns mockSettingsRepository

    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    // stub the real implementation
    every { PlaybackController.play(any(), any()) } returns Unit

    onView(withId(R.id.sound_list)).perform(
      RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
        click()
      )
    )

    verify(exactly = 1) { PlaybackController.play(any(), "birds") }
  }

  @Test
  fun testRecyclerViewItem_stopButton() {
    every { PlaybackController.stop(any(), any()) } returns Unit
    val mockUpdateEvent = mockk<MediaPlayerService.PlaybackUpdateEvent>(relaxed = true) {
      every { players } returns hashMapOf("birds" to mockk(relaxed = true))
    }

    fragmentScenario.onFragment { it.onPlayerManagerUpdate(mockUpdateEvent) }
    onView(withId(R.id.sound_list)).perform(
      RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
        allOf(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
          hasDescendant(
            allOf(withId(R.id.play_indicator), withEffectiveVisibility(Visibility.VISIBLE))
          )
        ),
        click()
      )
    )

    verify(exactly = 1) { PlaybackController.stop(any(), "birds") }
  }

  @Test
  fun testRecyclerViewItem_volumeSlider() {
    val mockPlayer = mockk<Player>(relaxed = true) {
      every { volume } returns Player.DEFAULT_VOLUME
    }

    val mockUpdateEvent = mockk<MediaPlayerService.PlaybackUpdateEvent>(relaxed = true) {
      every { players } returns hashMapOf("birds" to mockPlayer)
    }

    every {
      mockEventBus.getStickyEvent(MediaPlayerService.PlaybackUpdateEvent::class.java)
    } returns mockUpdateEvent

    fragmentScenario.onFragment { it.onPlayerManagerUpdate(mockUpdateEvent) }
    val expectedVolumes = arrayOf(0, Player.MAX_VOLUME, Random.nextInt(1, Player.MAX_VOLUME))
    for (expectedVolume in expectedVolumes) {
      onView(withId(R.id.sound_list)).perform(
        RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
          EspressoX.clickInItem(R.id.volume_button)
        )
      )

      onView(withId(R.id.volume_slider))
        .check(matches(isDisplayed()))
        .perform(EspressoX.slide(expectedVolume.toFloat()))

      onView(withId(R.id.positive)).perform(click())
      verify(exactly = 1, timeout = 5000L) { mockPlayer.setVolume(expectedVolume) }
    }
  }

  @Test
  fun testRecyclerViewItem_timePeriodSlider() {
    val mockPlayer = mockk<Player>(relaxed = true) {
      every { timePeriod } returns Player.DEFAULT_TIME_PERIOD
    }

    val mockUpdateEvent = mockk<MediaPlayerService.PlaybackUpdateEvent>(relaxed = true) {
      every { players } returns hashMapOf("rolling_thunder" to mockPlayer)
    }

    every {
      mockEventBus.getStickyEvent(MediaPlayerService.PlaybackUpdateEvent::class.java)
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
            EspressoX.clickInItem(R.id.time_period_button)
          )
        )

      onView(withId(R.id.time_period_slider))
        .check(matches(isDisplayed()))
        .perform(EspressoX.slide(expectedTimePeriod.toFloat()))

      onView(withId(R.id.positive)).perform(click())
      verify(exactly = 1) { mockPlayer.timePeriod = expectedTimePeriod }
    }
  }

  @Test
  fun testSavePresetButton_onUnknownPresetPlayback() {
    val mockUpdateEvent = mockk<MediaPlayerService.PlaybackUpdateEvent>(relaxed = true) {
      every { players } returns mockk(relaxed = true)
    }

    val states = arrayOf(
      PlaybackStateCompat.STATE_PLAYING,
      PlaybackStateCompat.STATE_PAUSED,
      PlaybackStateCompat.STATE_STOPPED
    )

    val visibility = arrayOf(
      Visibility.VISIBLE,
      Visibility.GONE,
      Visibility.GONE,
    )

    for (i in states.indices) {
      every { mockUpdateEvent.state } returns states[i]
      fragmentScenario.onFragment {
        it.onPlayerManagerUpdate(mockUpdateEvent)
      }

      onView(withId(R.id.save_preset_button))
        .check(matches(withEffectiveVisibility(visibility[i])))
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
    every { Preset.from("", mockPlayers.values) } returns preset
    every { mockPresetRepository.list() } returns arrayOf(preset)
    fragmentScenario.onFragment { fragment ->
      fragment.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlaybackStateCompat.STATE_PLAYING
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
      mockEventBus.getStickyEvent(MediaPlayerService.PlaybackUpdateEvent::class.java)
    } returns mockk(relaxed = true) {
      every { state } returns PlaybackStateCompat.STATE_PLAYING
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
        EspressoX.clickInItem(R.id.volume_button)
      )
    )

    onView(withId(R.id.volume_slider))
      .check(matches(isDisplayed()))
      .perform(EspressoX.slide(6f))

    onView(withId(R.id.positive)).perform(click())
    verify(exactly = 1) { requireNotNull(mockPlayers["birds"]).setVolume(6) }
    onView(allOf(withId(R.id.save_preset_button), withEffectiveVisibility(Visibility.VISIBLE)))
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

    every { mockPresetRepository.list() } returns arrayOf(
      mockk(relaxed = true) {
        every { name } returns "test-exists"
      }
    )

    fragmentScenario.onFragment { fragment ->
      fragment.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlaybackStateCompat.STATE_PLAYING
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
    verify { mockPresetRepository.create(capture(presetSlot)) }

    for (playerState in presetSlot.captured.playerStates) {
      assertEquals(mockPlayers[playerState.soundKey]?.volume, playerState.volume)
      assertEquals(mockPlayers[playerState.soundKey]?.timePeriod, playerState.timePeriod)
    }

    verify(exactly = 1) { mockReviewFlowProvider.maybeAskForReview(any()) }
  }

  @Test
  fun testShouldDisplaySoundIcons() {
    val inputs = arrayOf(false, true)
    val outputs = arrayOf(Visibility.GONE, Visibility.VISIBLE)
    for (i in inputs.indices) {
      every { mockSettingsRepository.shouldDisplaySoundIcons() } returns inputs[i]
      fragmentScenario.recreate()
      onView(withId(R.id.sound_list))
        .perform(
          RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
            allOf(
              hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
              hasDescendant(allOf(withId(R.id.icon), withEffectiveVisibility(outputs[i])))
            ),
            EspressoX.noop()
          ),
        )
    }
  }
}
