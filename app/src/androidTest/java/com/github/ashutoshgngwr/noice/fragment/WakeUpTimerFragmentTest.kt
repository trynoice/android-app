package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.sound.Preset
import io.mockk.*
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WakeUpTimerFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var fragmentScenario: FragmentScenario<WakeUpTimerFragment>

  @Before
  fun setup() {
    mockkObject(Preset.Companion)
    mockkObject(WakeUpTimerManager)
    every { WakeUpTimerManager.set(any(), any()) } returns Unit
    fragmentScenario = launchFragmentInContainer<WakeUpTimerFragment>(null, R.style.Theme_App)
  }

  @Test
  fun testInitialLayout_whenTimerIsNotPreScheduled() {
    onView(withId(R.id.countdown_view))
      .check(matches(withText("00h 00m 00s")))

    onView(withId(R.id.button_select_preset))
      .check(matches(isEnabled()))
      .check(matches(withText(R.string.select_preset)))

    onView(withId(R.id.duration_picker))
      .check(matches(not(isEnabled())))
  }

  @Test
  fun testInitialLayout_whenTimerIsPreScheduled() {
    every { Preset.findByName(any(), "test") } returns mockk()
    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetName } returns "test"
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    fragmentScenario.recreate()
    onView(withId(R.id.countdown_view))
      .check(matches(not(withText("00h 00m 00s"))))

    onView(withId(R.id.button_select_preset))
      .check(matches(isEnabled()))
      .check(matches(withText("test")))

    onView(withId(R.id.duration_picker))
      .check(matches(isEnabled()))
  }

  @Test
  fun testInitialLayout_whenTimerIsPreScheduled_andPresetIsDeletedFromStorage() {
    every { Preset.findByName(any(), "test") } returns null
    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetName } returns "test"
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    fragmentScenario.recreate()
    onView(withId(R.id.countdown_view))
      .check(matches(withText("00h 00m 00s")))

    onView(withId(R.id.button_select_preset))
      .check(matches(isEnabled()))
      .check(matches(withText(R.string.select_preset)))

    onView(withId(R.id.duration_picker))
      .check(matches(not(isEnabled())))
  }

  @Test
  fun testSetTimer() {
    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf(
      mockk(relaxed = true) { every { name } returns "test-1" },
      mockk(relaxed = true) { every { name } returns "test-2" }
    )

    onView(withId(R.id.button_select_preset))
      .check(matches(withText(R.string.select_preset)))
      .perform(click())

    EspressoX.waitForView(withId(android.R.id.list), 100, 5)
      .check(matches(withChild(withText("test-1"))))
      .check(matches(withChild(withText("test-2"))))

    onView(withText("test-1"))
      .perform(click())

    onView(withId(R.id.button_select_preset))
      .check(matches(withText("test-1")))

    verify(exactly = 0) { WakeUpTimerManager.set(any(), any()) }
    onView(withId(R.id.duration_picker))
      .check(matches(isEnabled()))
      .perform(EspressoX.addDurationToPicker(10L))

    val timerSlot = slot<WakeUpTimerManager.Timer>()
    verify(exactly = 1) { WakeUpTimerManager.set(any(), capture(timerSlot)) }
    assertEquals("test-1", timerSlot.captured.presetName)
    val timeRange = System.currentTimeMillis() until System.currentTimeMillis() + 10000L
    assertTrue(timerSlot.captured.atMillis in timeRange)

    onView(EspressoX.withDurationPickerResetButton(withId(R.id.duration_picker)))
      .check(matches(isEnabled()))
  }

  @Test
  fun testCancelTimer() {
    every { Preset.findByName(any(), "test") } returns mockk()
    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetName } returns "test"
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    fragmentScenario.recreate()
    clearMocks(WakeUpTimerManager)
    onView(EspressoX.withDurationPickerResetButton(withId(R.id.duration_picker)))
      .check(matches(isEnabled()))
      .perform(scrollTo(), click())

    verify(exactly = 1) { WakeUpTimerManager.cancel(any()) }
  }
}
