package com.github.ashutoshgngwr.noice.fragment

import android.os.SystemClock
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.databinding.SleepTimerFragmentBinding
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SleepTimerFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var fragmentScenario: FragmentScenario<SleepTimerFragment>

  @Before
  fun setup() {
    mockkObject(InAppReviewFlowManager, MediaPlayerService.Companion)
    every { MediaPlayerService.scheduleStopPlayback(any(), any()) } returns Unit

    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testDurationPickerListener_onSleepPreScheduled() {
    val before = SystemClock.uptimeMillis()
    every { MediaPlayerService.getScheduledStopPlaybackRemainingDurationMillis() } returns 60 * 1000
    onView(withId(R.id.duration_picker)).perform(EspressoX.addDurationToPicker(60))
    verify(exactly = 1) {
      MediaPlayerService.scheduleStopPlayback(any(), withArg {
        val delta = SystemClock.uptimeMillis() - before
        val duration = TimeUnit.SECONDS.toMillis(120)
        assertTrue(it in (duration - delta)..(duration))
      })
    }
  }

  @Test
  fun testDurationPickerListener() {
    val before = SystemClock.uptimeMillis()
    onView(withId(R.id.duration_picker)).perform(EspressoX.addDurationToPicker(300))
    verify(exactly = 1) {
      MediaPlayerService.scheduleStopPlayback(any(), withArg {
        val delta = SystemClock.uptimeMillis() - before
        val duration = TimeUnit.SECONDS.toMillis(300)
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
    verify(exactly = 1) { MediaPlayerService.scheduleStopPlayback(any(), 0L) }
  }
}
