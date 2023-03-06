package com.github.ashutoshgngwr.noice.fragment

import android.os.SystemClock
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.HiltFragmentScenario
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SleepTimerFragmentBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SleepTimerFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var fragmentScenario: HiltFragmentScenario<SleepTimerFragment>

  @BindValue
  internal lateinit var mockPlaybackController: PlaybackController

  @Before
  fun setup() {
    mockPlaybackController = mockk(relaxed = true)
    fragmentScenario = launchFragmentInHiltContainer()
  }

  @Test
  fun testDurationPickerListener_onSleepPreScheduled() {
    val before = SystemClock.uptimeMillis()
    every { mockPlaybackController.getStopScheduleRemainingMillis() } returns 60L * 1000
    onView(withId(R.id.duration_picker)).perform(EspressoX.addDurationToPicker(60))
    verify(exactly = 1) {
      mockPlaybackController.scheduleStop(withArg {
        val delta = SystemClock.uptimeMillis() - before
        val duration = 120L * 1000
        assertTrue(it in (duration - delta)..(duration))
      })
    }
  }

  @Test
  fun testDurationPickerListener() {
    val before = SystemClock.uptimeMillis()
    onView(withId(R.id.duration_picker)).perform(EspressoX.addDurationToPicker(300))
    verify(exactly = 1) {
      mockPlaybackController.scheduleStop(withArg {
        val delta = SystemClock.uptimeMillis() - before
        val duration = 300L * 1000
        assertTrue(it in (duration - delta)..(duration))
      })
    }
  }

  @Test
  fun testResetButton_shouldNotBeEnabledByDefault() {
    onView(EspressoX.withDurationPickerResetButton(withId(R.id.duration_picker)))
      .check(matches(not(isEnabled())))
  }

  @Test
  fun testResetButton_onAutoSleepScheduled() {
    fragmentScenario.onFragment {
      SleepTimerFragmentBinding.bind(it.requireView()).also { binding ->
        binding.durationPicker.setResetButtonEnabled(true)
      }
    }

    onView(EspressoX.withDurationPickerResetButton(withId(R.id.duration_picker)))
      .perform(scrollTo(), click())

    onView(withText(R.string.auto_sleep_schedule_cancelled)).check(matches(isDisplayed()))
    verify(exactly = 1) { mockPlaybackController.clearScheduledAutoStop() }
  }
}
