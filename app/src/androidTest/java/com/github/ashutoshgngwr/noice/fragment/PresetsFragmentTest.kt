package com.github.ashutoshgngwr.noice.fragment

//import android.content.Intent
//import android.widget.Button
//import androidx.core.content.pm.ShortcutInfoCompat
//import androidx.core.content.pm.ShortcutManagerCompat
//import androidx.test.espresso.Espresso.onView
//import androidx.test.espresso.action.ViewActions.click
//import androidx.test.espresso.action.ViewActions.replaceText
//import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
//import androidx.test.espresso.assertion.ViewAssertions.matches
//import androidx.test.espresso.contrib.RecyclerViewActions
//import androidx.test.espresso.intent.Intents
//import androidx.test.espresso.intent.Intents.intended
//import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
//import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
//import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
//import androidx.test.espresso.matcher.ViewMatchers.*
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import com.github.ashutoshgngwr.noice.EspressoX
//import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
//import com.github.ashutoshgngwr.noice.HiltFragmentScenario
//import com.github.ashutoshgngwr.noice.R
//import com.github.ashutoshgngwr.noice.di.ReviewFlowProviderModule
//import com.github.ashutoshgngwr.noice.activity.ShortcutHandlerActivity
//import com.github.ashutoshgngwr.noice.engine.PlaybackController
//import com.github.ashutoshgngwr.noice.model.PlayerState
//import com.github.ashutoshgngwr.noice.model.Preset
//import com.github.ashutoshgngwr.noice.metrics.ReviewFlowProvider
//import com.github.ashutoshgngwr.noice.repository.PresetRepository
//import dagger.hilt.android.testing.BindValue
//import dagger.hilt.android.testing.HiltAndroidRule
//import dagger.hilt.android.testing.HiltAndroidTest
//import dagger.hilt.android.testing.UninstallModules
//import io.mockk.clearStaticMockk
//import io.mockk.every
//import io.mockk.mockk
//import io.mockk.mockkStatic
//import io.mockk.slot
//import io.mockk.unmockkAll
//import io.mockk.verify
//import kotlinx.coroutines.flow.flowOf
//import org.hamcrest.Matchers.*
//import org.junit.After
//import org.junit.Assert.*
//import org.junit.Before
//import org.junit.Rule
//import org.junit.Test
//import org.junit.runner.RunWith
//
//@HiltAndroidTest
//@UninstallModules(ReviewFlowProviderModule::class)
//@RunWith(AndroidJUnit4::class)
//class PresetsFragmentTest {
//
//  @get:Rule
//  val hiltRule = HiltAndroidRule(this)
//
//  private lateinit var fragmentScenario: HiltFragmentScenario<PresetsFragment>
//  private lateinit var mockPreset: Preset
//
//  @BindValue
//  internal lateinit var mockPresetRepository: PresetRepository
//
//  @BindValue
//  internal lateinit var mockPlaybackController: PlaybackController
//
//  @BindValue
//  internal lateinit var mockReviewFlowProvider: ReviewFlowProvider
//
//  @Before
//  fun setup() {
//    mockkStatic(ShortcutManagerCompat::class)
//
//    mockPreset = mockk(relaxed = true) {
//      every { id } returns "test-id"
//      every { name } returns "test"
//      every { playerStates } returns arrayOf(
//        PlayerState("test-1", PlaybackController.DEFAULT_SOUND_VOLUME),
//        PlayerState("test-2", PlaybackController.DEFAULT_SOUND_VOLUME),
//      )
//    }
//
//    val presets = listOf(
//      mockPreset,
//      mockk(relaxed = true) {
//        every { name } returns "test-exists"
//      },
//    )
//
//    mockPresetRepository = mockk {
//      every { list() } returns presets
//      every { listFlow() } returns flowOf(presets)
//    }
//
//    mockPlaybackController = mockk(relaxed = true)
//    mockReviewFlowProvider = mockk(relaxed = true)
//    fragmentScenario = launchFragmentInHiltContainer()
//  }
//
//  @After
//  fun teardown() {
//    unmockkAll()
//  }
//
//  @Test
//  fun testInitialLayout() {
//    onView(withId(R.id.empty_list_indicator))
//      .check(matches(withEffectiveVisibility(Visibility.GONE)))
//
//    every { mockPresetRepository.list() } returns listOf()
//    fragmentScenario.recreate()
//    onView(withId(R.id.empty_list_indicator))
//      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
//  }
//
//  @Test
//  fun testRecyclerViewItem_playButton() {
//    onView(withId(R.id.list)).perform(
//      RecyclerViewActions.actionOnItem<PresetViewHolder>(
//        hasDescendant(allOf(withId(R.id.title), withText("test"))),
//        EspressoX.clickOn(R.id.play_button)
//      )
//    )
//
//    verify(exactly = 1) { mockPlaybackController.play(mockPreset) }
//  }
//
//  @Test
//  fun testRecyclerViewItem_stopButton() {
//    // ensure that PresetsFragment assumes it is playing a preset
//    every { Preset.from(any<String>(), any()) } returns mockPreset
//    fragmentScenario.onFragment {
//      it.onPlayerManagerUpdate(mockk(relaxed = true) {
//        every { state } returns PlaybackStateCompat.STATE_PLAYING
//      })
//    }
//
//    onView(withId(R.id.list)).perform(
//      RecyclerViewActions.actionOnItem<PresetsFragment.ViewHolder>(
//        hasDescendant(allOf(withId(R.id.title), withText("test"))),
//        EspressoX.clickOn(R.id.play_button)
//      )
//    )
//
//    verify(exactly = 1) { mockPlaybackController.stop() }
//  }
//
//  @Test
//  fun testRecyclerViewItem_shareOption() {
//    val presetUri = "test-preset-uri"
//    every { mockPresetRepository.writeAsUrl(mockPreset) } returns presetUri
//
//    // open context menu
//    onView(withId(R.id.list)).perform(
//      RecyclerViewActions.actionOnItem<PresetViewHolder>(
//        hasDescendant(allOf(withId(R.id.title), withText("test"))),
//        EspressoX.clickOn(R.id.menu_button)
//      )
//    )
//
//    try {
//      Intents.init()
//      onView(withText(R.string.share)).perform(click()) // select share option
//      intended(
//        EspressoX.hasIntentChooser(
//          allOf(
//            hasAction(Intent.ACTION_SEND),
//            hasExtra(Intent.EXTRA_TEXT, presetUri)
//          )
//        )
//      )
//    } finally {
//      Intents.release()
//    }
//  }
//
//  @Test
//  fun testRecyclerViewItem_deleteOption() {
//    // open context menu
//    onView(withId(R.id.list)).perform(
//      RecyclerViewActions.actionOnItem<PresetViewHolder>(
//        hasDescendant(allOf(withId(R.id.title), withText("test"))),
//        EspressoX.clickOn(R.id.menu_button)
//      )
//    )
//
//    onView(withText(R.string.delete)).perform(click()) // select delete option
//    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
//      .perform(click()) // click delete button in confirmation dialog
//
//    onView(withText("test")).check(doesNotExist())
//    verify(exactly = 1) {
//      mockPresetRepository.delete("test-id")
//      ShortcutManagerCompat.removeDynamicShortcuts(any(), listOf("test-id"))
//      mockReviewFlowProvider.maybeAskForReview(any())
//    }
//  }
//
//  @Test
//  fun testRecyclerViewItem_deleteOption_onPresetPlaying() {
//    // ensure that PresetsFragment assumes it is playing a preset
//    every { Preset.from(any<String>(), any()) } returns mockPreset
//    every { mockPresetRepository.delete(any()) } returns true
//
//    fragmentScenario.onFragment {
//      it.onPlayerManagerUpdate(mockk(relaxed = true) {
//        every { state } returns PlaybackStateCompat.STATE_PLAYING
//      })
//    }
//
//    // open context menu
//    onView(withId(R.id.list)).perform(
//      RecyclerViewActions.actionOnItem<PresetsFragment.ViewHolder>(
//        hasDescendant(allOf(withId(R.id.title), withText("test"))),
//        EspressoX.clickOn(R.id.menu_button)
//      )
//    )
//
//    onView(withText(R.string.delete)).perform(click()) // select delete option
//    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
//      .perform(click()) // click delete button in confirmation dialog
//
//    // should publish a stop playback event if preset was playing
//    verify(exactly = 1) { mockPlaybackController.stop() }
//  }
//
//  @Test
//  fun testRecyclerViewItem_renameOption() {
//    every { mockPresetRepository.update(any()) } returns Unit
//
//    // open context menu
//    onView(withId(R.id.list)).perform(
//      RecyclerViewActions.actionOnItem<PresetViewHolder>(
//        hasDescendant(allOf(withId(R.id.title), withText("test"))),
//        EspressoX.clickOn(R.id.menu_button)
//      )
//    )
//
//    onView(withText(R.string.rename)).perform(click()) // select rename option
//    onView(withId(R.id.editText))
//      .check(matches(isDisplayed())) // check if the rename dialog was displayed
//      .perform(replaceText("test-exists")) // replace text in input field
//
//    onView(allOf(instanceOf(Button::class.java), withText(R.string.save)))
//      .check(matches(not(isEnabled())))
//
//    onView(withId(R.id.editText))
//      .check(matches(isDisplayed()))
//      .perform(replaceText("test-does-not-exists"))
//
//    onView(allOf(instanceOf(Button::class.java), withText(R.string.save)))
//      .check(matches(isEnabled()))
//      .perform(click()) // click on positive button
//
//    val presetSlot = slot<Preset>()
//    verify(exactly = 1) {
//      mockPreset.copy(name = "test-does-not-exists")
//      mockPresetRepository.update(capture(presetSlot))
//      mockReviewFlowProvider.maybeAskForReview(any())
//    }
//
//    assertEquals(mockPreset, presetSlot.captured)
//  }
//
//  @Test
//  fun testRecyclerViewItem_AddToHomeScreenOption() {
//    val pinShortcutSupportedExpectations = arrayOf(false, true)
//    val expectedRequestPinShortcutCalls = arrayOf(0, 1)
//
//    for (i in pinShortcutSupportedExpectations.indices) {
//      // prevent system dialog from showing up.
//      every { ShortcutManagerCompat.requestPinShortcut(any(), any(), any()) } returns true
//      every {
//        ShortcutManagerCompat.isRequestPinShortcutSupported(any())
//      } returns pinShortcutSupportedExpectations[i]
//
//      // open context menu
//      onView(withId(R.id.list)).perform(
//        RecyclerViewActions.actionOnItem<PresetViewHolder>(
//          hasDescendant(allOf(withId(R.id.title), withText("test"))),
//          EspressoX.clickOn(R.id.menu_button)
//        )
//      )
//
//      onView(withText(R.string.add_to_home_screen)).perform(click()) // select add to home screen option
//
//      val shortcutInfoSlot = slot<ShortcutInfoCompat>()
//      verify(exactly = expectedRequestPinShortcutCalls[i]) {
//        ShortcutManagerCompat.requestPinShortcut(any(), capture(shortcutInfoSlot), any())
//      }
//
//      if (pinShortcutSupportedExpectations[i]) {
//        assertTrue(
//          "should capture a ShortcutInfo from requestPinShortcut() call",
//          shortcutInfoSlot.isCaptured
//        )
//
//        assertNotNull(shortcutInfoSlot.captured.id)
//        assertEquals("test", shortcutInfoSlot.captured.shortLabel)
//
//        val intent = shortcutInfoSlot.captured.intent
//        assertEquals(ShortcutHandlerActivity::class.qualifiedName, intent.component?.className)
//        assertNotNull(intent.getStringExtra(ShortcutHandlerActivity.EXTRA_SHORTCUT_ID))
//        assertEquals("test-id", intent.getStringExtra(ShortcutHandlerActivity.EXTRA_PRESET_ID))
//      } else {
//        assertFalse(
//          "should not capture a ShortcutInfo from requestPinShortcut() call",
//          shortcutInfoSlot.isCaptured
//        )
//
//        onView(withId(com.google.android.material.R.id.snackbar_text))
//          .check(matches(withText(R.string.pinned_shortcuts_not_supported)))
//      }
//
//      clearStaticMockk(ShortcutManagerCompat::class)
//    }
//  }
//
//  @Test
//  fun testRecyclerViewItem_addToAppShortcuts() {
//    every { ShortcutManagerCompat.addDynamicShortcuts(any(), any()) } returns true
//    every { ShortcutManagerCompat.getDynamicShortcuts(any()) } returns mutableListOf()
//
//    // open context menu
//    onView(withId(R.id.list)).perform(
//      RecyclerViewActions.actionOnItem<PresetViewHolder>(
//        hasDescendant(allOf(withId(R.id.title), withText("test"))),
//        EspressoX.clickOn(R.id.menu_button)
//      )
//    )
//
//    onView(withText(R.string.add_to_app_shortcuts)).perform(click())
//
//    val shortcutInfoSlot = slot<List<ShortcutInfoCompat>>()
//    verify(exactly = 1) {
//      ShortcutManagerCompat.addDynamicShortcuts(any(), capture(shortcutInfoSlot))
//    }
//
//    assertEquals(1, shortcutInfoSlot.captured.size)
//    assertEquals("test-id", shortcutInfoSlot.captured[0].id)
//  }
//
//  @Test
//  fun testRecyclerViewItem_removeFromAppShortcuts() {
//    every { ShortcutManagerCompat.getDynamicShortcuts(any()) } returns listOf(
//      mockk(relaxed = true) {
//        every { id } returns mockPreset.id
//      }
//    )
//
//    // open context menu
//    onView(withId(R.id.list)).perform(
//      RecyclerViewActions.actionOnItem<PresetViewHolder>(
//        hasDescendant(allOf(withId(R.id.title), withText("test"))),
//        EspressoX.clickOn(R.id.menu_button)
//      )
//    )
//
//    onView(withText(R.string.remove_from_app_shortcuts))
//      .inRoot(isPlatformPopup())
//      .perform(click())
//
//    verify(exactly = 1) {
//      ShortcutManagerCompat.removeDynamicShortcuts(any(), listOf("test-id"))
//    }
//  }
//}
