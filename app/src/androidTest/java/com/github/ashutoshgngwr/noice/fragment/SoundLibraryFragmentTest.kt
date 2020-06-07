package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
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
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
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

  @InjectMockKs(injectImmutable = true, overrideValues = true)
  private lateinit var fragment: SoundLibraryFragment
  private lateinit var fragmentScenario: FragmentScenario<SoundLibraryFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInContainer<SoundLibraryFragment>(null, R.style.Theme_App)
    fragmentScenario.onFragment { fragment = it } // just for mock injection
    MockKAnnotations.init(this)
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    onView(withId(R.id.list_sound)).perform(
      RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
        ViewActionsX.clickOn(R.id.button_play)
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
    onView(withId(R.id.list_sound)).perform(
      RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
        ViewActionsX.clickOn(R.id.button_play)
      )
    )

    verify(exactly = 1) { eventBus.post(MediaPlayerService.StopPlayerEvent("birds")) }
  }

  @Test
  fun testRecyclerViewItem_volumeSeekBar() {
    val mockPlayer = mockk<Player>(relaxed = true) {
      every { volume } returns Player.DEFAULT_VOLUME
    }

    val mockUpdateEvent = mockk<MediaPlayerService.OnPlayerManagerUpdateEvent>(relaxed = true) {
      every { players } returns hashMapOf("birds" to mockPlayer)
    }

    fragmentScenario.onFragment { it.onPlayerManagerUpdate(mockUpdateEvent) }
    val expectedVolumes = arrayOf(0, Player.MAX_VOLUME, Random.nextInt(1, Player.MAX_VOLUME))
    for (expectedVolume in expectedVolumes) {
      onView(withId(R.id.list_sound)).perform(
        RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
          ViewActionsX.seekProgress(R.id.seekbar_volume, expectedVolume)
        )
      )

      verify(exactly = 1) { mockPlayer.setVolume(expectedVolume) }
    }
  }

  @Test
  fun testRecyclerViewItem_timePeriodSeekBar() {
    val mockPlayer = mockk<Player>(relaxed = true) {
      every { timePeriod } returns Player.DEFAULT_TIME_PERIOD
    }

    val mockUpdateEvent = mockk<MediaPlayerService.OnPlayerManagerUpdateEvent>(relaxed = true) {
      every { players } returns hashMapOf("rolling_thunder" to mockPlayer)
    }

    fragmentScenario.onFragment { it.onPlayerManagerUpdate(mockUpdateEvent) }
    val expectedTimePeriods =
      arrayOf(1, Player.MAX_TIME_PERIOD, Random.nextInt(2, Player.MAX_TIME_PERIOD))

    for (expectedTimePeriod in expectedTimePeriods) {
      onView(withId(R.id.list_sound))
        .perform(
          RecyclerViewActions.scrollTo<SoundLibraryFragment.ViewHolder>(
            hasDescendant(allOf(withId(R.id.title), withText(R.string.rolling_thunder)))
          ),
          RecyclerViewActions.actionOnItem<SoundLibraryFragment.ViewHolder>(
            hasDescendant(allOf(withId(R.id.title), withText(R.string.rolling_thunder))),
            ViewActionsX.seekProgress(R.id.seekbar_time_period, expectedTimePeriod)
          )
        )

      verify(exactly = 1) { mockPlayer.timePeriod = expectedTimePeriod }
    }
  }

  @Test
  fun testSavePresetButton_onTogglingPlayback() {
    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    fragmentScenario.onFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
      })
    }

    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    fragmentScenario.onFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.STOPPED
      })
    }

    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.GONE)))
  }

  @Test
  fun testSavePresetButton_onUnknownPresetPlayback() {
    fragmentScenario.onFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
        every { players } returns mockk(relaxed = true)
      })
    }

    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
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

    onView(withId(R.id.fab_save_preset)).check(matches(withEffectiveVisibility(Visibility.GONE)))
  }

  @Test
  fun testSavePresetButton_onClick() {
    val mockPlayers: Map<String, Player> = hashMapOf(
      "birds" to mockk(relaxed = true),
      "rolling_thunder" to mockk(relaxed = true)
    )

    val preset = Preset.from("test", mockPlayers.values)
    mockkObject(Preset.Companion)
    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf()
    every { Preset.from("", mockPlayers.values) } returns preset
    fragmentScenario.onFragment { fragment ->
      fragment.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
        every { players } returns mockPlayers
      })
    }

    onView(withId(R.id.fab_save_preset))
      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
      .perform(click())

    onView(allOf(withId(R.id.title), withText(R.string.save_preset)))
      .check(matches(isDisplayed()))

    onView(withId(R.id.editText))
      .check(matches(isDisplayed()))
      .perform(replaceText("test"))

    onView(allOf(withId(R.id.positive), withText(R.string.save))).perform(click())
    onView(withId(R.id.fab_save_preset)).check(matches(not(isDisplayed())))
    onView(withId(com.google.android.material.R.id.snackbar_text))
      .check(matches(withText(R.string.preset_saved)))

    verify { Preset.appendToUserPreferences(any(), preset) }
  }
}
