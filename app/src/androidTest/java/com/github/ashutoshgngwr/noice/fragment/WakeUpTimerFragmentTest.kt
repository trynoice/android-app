package com.github.ashutoshgngwr.noice.fragment

import android.icu.util.Calendar
import androidx.core.content.edit
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WakeUpTimerFragmentTest {

  private lateinit var mockPresetRepository: PresetRepository
  private lateinit var mockReviewFlowProvider: ReviewFlowProvider
  private lateinit var fragmentScenario: FragmentScenario<WakeUpTimerFragment>

  @Before
  fun setup() {
    mockReviewFlowProvider = mockk(relaxed = true)
    ApplicationProvider.getApplicationContext<NoiceApplication>()
      .reviewFlowProvider = mockReviewFlowProvider

    mockPresetRepository = mockk {
      every { get(null) } returns null
      every { list() } returns emptyArray()
    }

    every { PresetRepository.newInstance(any()) } returns mockPresetRepository
    every { WakeUpTimerManager.set(any(), any()) } returns Unit
    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @After
  fun teardown() {
    unmockkAll()
    with(PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())) {
      edit { clear() }
    }
  }

  @Test
  fun testInitialLayout_whenTimerIsNotPreScheduled() {
    onView(withId(R.id.select_preset_button))
      .check(matches(isEnabled()))
      .check(matches(withText(R.string.select_preset)))

    onView(withId(R.id.set_time_button))
      .check(matches(not(isEnabled())))

    onView(withId(R.id.reset_time_button))
      .check(matches(not(isEnabled())))
  }

  @Test
  fun testInitialLayout_whenTimerIsPreScheduled() {
    val expectedPresetID = "test-preset-id"
    val expectedPresetName = "test-preset-name"
    every { mockPresetRepository.get(expectedPresetID) } returns mockk {
      every { name } returns expectedPresetName
    }

    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetID } returns expectedPresetID
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    // fragmentScenario.recreate() // for whatever reasons, doesn't invoke Fragment.onViewCreated().
    fragmentScenario.onFragment { it.onViewCreated(it.requireView(), null) }

    onView(withId(R.id.select_preset_button))
      .check(matches(isEnabled()))
      .check(matches(withText(expectedPresetName)))

    onView(withId(R.id.set_time_button))
      .check(matches(isEnabled()))

    onView(withId(R.id.reset_time_button))
      .check(matches(isEnabled()))
  }

  @Test
  fun testInitialLayout_whenTimerIsPreScheduled_andPresetIsDeletedFromStorage() {
    every { mockPresetRepository.get("test") } returns null
    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetID } returns "test"
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    fragmentScenario.recreate()
    onView(withId(R.id.select_preset_button))
      .check(matches(isEnabled()))
      .check(matches(withText(R.string.select_preset)))

    onView(withId(R.id.set_time_button))
      .check(matches(not(isEnabled())))

    onView(withId(R.id.reset_time_button))
      .check(matches(not(isEnabled())))
  }

  @Test
  fun testSelectPreset_withoutPresets() {
    every { mockPresetRepository.list() } returns arrayOf()
    onView(withId(R.id.select_preset_button))
      .check(matches(withText(R.string.select_preset)))
      .perform(click())

    onView(withText(R.string.preset_info__description))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testSetTimer() {
    val savedPreset = mockk<Preset>(relaxed = true) {
      every { id } returns "test-id-1"
      every { name } returns "test-1"
    }

    every { mockPresetRepository.get("test-id-1") } returns savedPreset
    every { mockPresetRepository.list() } returns arrayOf(
      savedPreset,
      mockk(relaxed = true) {
        every { id } returns "test-id-2"
        every { name } returns "test-2"
      }
    )


    onView(withId(R.id.select_preset_button))
      .perform(click())

    onView(withId(android.R.id.list))
      .check(matches(withChild(withText("test-1"))))
      .check(matches(withChild(withText("test-2"))))

    onView(withText("test-1"))
      .perform(click())

    onView(withId(R.id.select_preset_button))
      .check(matches(withText("test-1")))

    verify(exactly = 0) { WakeUpTimerManager.set(any(), any()) }

    onView(withId(R.id.time_picker))
      .perform(PickerActions.setTime(1, 2))

    onView(withId(R.id.set_time_button))
      .check(matches(isEnabled()))
      .perform(scrollTo(), click())

    val calendar = Calendar.getInstance()
    val timerSlot = slot<WakeUpTimerManager.Timer>()

    try {
      verify(exactly = 1) {
        WakeUpTimerManager.set(any(), capture(timerSlot))
        mockReviewFlowProvider.maybeAskForReview(any())
      }
    } finally {
      clearMocks(WakeUpTimerManager, mockReviewFlowProvider)
    }

    calendar.timeInMillis = timerSlot.captured.atMillis
    assertEquals(1, calendar.get(Calendar.HOUR_OF_DAY))
    assertEquals(2, calendar.get(Calendar.MINUTE))
    assertEquals("test-id-1", timerSlot.captured.presetID)

    onView(withId(R.id.reset_time_button)).check(matches(isEnabled()))
    onView(withSubstring("The alarm will go off in"))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testCancelTimer() {
    every { mockPresetRepository.get("test-id") } returns mockk {
      every { name } returns "test-name"
    }

    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetID } returns "test-id"
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    fragmentScenario.recreate()
    clearMocks(WakeUpTimerManager)
    onView(withId(R.id.reset_time_button))
      .check(matches(isEnabled()))
      .perform(scrollTo(), click())

    verify(exactly = 1) { WakeUpTimerManager.cancel(any()) }
  }

  @Test
  fun testLoadPreset() {
    val savedPresetID = "test-saved-preset-id"
    val savedPreset = mockk<Preset>(relaxed = true) {
      every { id } returns savedPresetID
      every { name } returns "test-saved-preset"
    }

    every { mockPresetRepository.list() } returns arrayOf(
      mockk(relaxed = true) {
        every { id } returns "test-not-saved-preset-id-1"
        every { name } returns "test-not-saved-preset-1"
      },
      savedPreset,
      mockk(relaxed = true) {
        every { id } returns "test-not-saved-preset-id-2"
        every { name } returns "test-not-saved-preset-2"
      }
    )

    every { WakeUpTimerManager.getLastUsedPresetID(any()) } returns savedPresetID
    every { mockPresetRepository.get(savedPresetID) } returns savedPreset

    fragmentScenario.recreate()
    onView(withId(R.id.select_preset_button))
      .check(matches(withText("test-saved-preset")))
  }

  @Test
  fun testNotSavedPreset_loadFirstPreset() {
    val savedPresetID = "test-not-saved-preset-id-1"
    val savedPreset = mockk<Preset>(relaxed = true) {
      every { id } returns savedPresetID
      every { name } returns "test-not-saved-preset-1"
    }

    every { mockPresetRepository.get(savedPresetID) } returns savedPreset
    every { mockPresetRepository.list() } returns arrayOf(
      savedPreset,
      mockk(relaxed = true) {
        every { id } returns "test-saved-preset-id"
        every { name } returns "test-saved-preset"
      },
      mockk(relaxed = true) {
        every { id } returns "test-not-saved-preset-id-2"
        every { name } returns "test-not-saved-preset-2"
      }
    )

    fragmentScenario.recreate()
    onView(withId(R.id.select_preset_button))
      .check(matches(withText("test-not-saved-preset-1")))
  }
}
