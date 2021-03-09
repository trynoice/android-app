package com.github.ashutoshgngwr.noice.fragment

import android.content.SharedPreferences
import android.widget.Button
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
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
import com.github.ashutoshgngwr.noice.ShortcutHandlerActivity
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var fragmentScenario: FragmentScenario<PresetFragment>

  // returned when Preset.readAllFromUserPreferences() is called
  private lateinit var mockPreset: Preset

  @Before
  fun setup() {
    mockPreset = mockk(relaxed = true) {
      every { id } returns "test-id"
      every { name } returns "test"
      every { playerStates } returns arrayOf(
        Preset.PlayerState("test-1", Player.DEFAULT_VOLUME, Player.DEFAULT_TIME_PERIOD),
        Preset.PlayerState("test-2", Player.MAX_VOLUME, Player.MAX_TIME_PERIOD)
      )
    }

    mockkObject(InAppReviewFlowManager, Preset.Companion, MediaPlayerService.Companion)
    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf(mockPreset)
    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @After
  fun teardown() {
    unmockkAll()
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
    // stub the original method. Without stubbing, mockk will also run the real implementation.
    every { MediaPlayerService.playPreset(any(), any()) } returns Unit

    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    verify(exactly = 1) { MediaPlayerService.playPreset(any(), "test-id") }
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

    verify(exactly = 1) { MediaPlayerService.stopPlayback(any()) }
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
    verify(exactly = 1) { MediaPlayerService.stopPlayback(any()) }
  }

  @Test
  fun testRecyclerViewItem_renameOption() {
    // if preset with given name already exists, save button should be disabled
    val mockValidator = mockk<(String) -> Boolean>()
    every { Preset.duplicateNameValidator(any()) } returns mockValidator
    every { mockValidator.invoke("test-exists") } returns true
    every { mockValidator.invoke("test-does-not-exists") } returns false

    // stub writing to the shared preferences part.
    every { Preset.writeAllToUserPreferences(any(), any()) } returns Unit

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

  @Test
  fun testRecyclerViewItem_AddToHomeScreenOption() {
    mockkStatic(ShortcutManagerCompat::class)

    val pinShortcutSupportedExpectations = arrayOf(false, true)
    val expectedRequestPinShortcutCalls = arrayOf(0, 1)

    for (i in pinShortcutSupportedExpectations.indices) {
      // prevent system dialog from showing up.
      every { ShortcutManagerCompat.requestPinShortcut(any(), any(), any()) } returns true
      every {
        ShortcutManagerCompat.isRequestPinShortcutSupported(any())
      } returns pinShortcutSupportedExpectations[i]

      // open context menu
      onView(withId(R.id.preset_list)).perform(
        RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText("test"))),
          EspressoX.clickInItem(R.id.menu_button)
        )
      )

      onView(withText(R.string.add_to_home_screen)).perform(click()) // select add to home screen option

      val shortcutInfoSlot = slot<ShortcutInfoCompat>()
      verify(exactly = expectedRequestPinShortcutCalls[i]) {
        ShortcutManagerCompat.requestPinShortcut(any(), capture(shortcutInfoSlot), any())
      }

      if (pinShortcutSupportedExpectations[i]) {
        assertTrue(
          "should capture a ShortcutInfo from requestPinShortcut() call",
          shortcutInfoSlot.isCaptured
        )

        assertNotNull(shortcutInfoSlot.captured.id)
        assertEquals("test", shortcutInfoSlot.captured.shortLabel)

        val intent = shortcutInfoSlot.captured.intent
        assertEquals(ShortcutHandlerActivity::class.qualifiedName, intent.component?.className)
        assertNotNull(intent.getStringExtra(ShortcutHandlerActivity.EXTRA_SHORTCUT_ID))
        assertEquals("test-id", intent.getStringExtra(ShortcutHandlerActivity.EXTRA_PRESET_ID))
      } else {
        assertFalse(
          "should not capture a ShortcutInfo from requestPinShortcut() call",
          shortcutInfoSlot.isCaptured
        )

        onView(withId(com.google.android.material.R.id.snackbar_text))
          .check(matches(withText(R.string.shortcuts_not_supported)))
      }

      clearStaticMockk(ShortcutManagerCompat::class)
    }
  }

  @Test
  fun testShouldShowAsHomeScreenSwitch_whenInitiallyUnchecked() {
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putBoolean(any(), any()) } returns this
    }

    mockkStatic(PreferenceManager::class)
    every {
      PreferenceManager.getDefaultSharedPreferences(any())
    } returns mockk(relaxed = true) {
      every { edit() } returns mockPrefsEditor
    }

    onView(withId(R.id.should_display_as_home_screen))
      .check(matches(isNotChecked()))
      .perform(click())

    verify(exactly = 1) {
      mockPrefsEditor.putBoolean(PresetFragment.PREF_SAVED_PRESETS_AS_HOME_SCREEN, true)
    }
  }

  @Test
  fun testShouldShowAsHomeScreenSwitch_whenInitiallyChecked() {
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putBoolean(any(), any()) } returns this
    }

    mockkStatic(PreferenceManager::class)
    every {
      PreferenceManager.getDefaultSharedPreferences(any())
    } returns mockk(relaxed = true) {
      every { getBoolean(PresetFragment.PREF_SAVED_PRESETS_AS_HOME_SCREEN, any()) } returns true
      every { edit() } returns mockPrefsEditor
    }

    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
    onView(withId(R.id.should_display_as_home_screen))
      .check(matches(isChecked()))
      .perform(click())

    verify(exactly = 1) {
      mockPrefsEditor.putBoolean(PresetFragment.PREF_SAVED_PRESETS_AS_HOME_SCREEN, false)
    }
  }
}
