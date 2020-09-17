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
import io.mockk.*
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.android.synthetic.main.fragment_sleep_timer.view.*
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepTimerFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  @RelaxedMockK
  private lateinit var eventBus: EventBus

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var fragment: SleepTimerFragment

  private lateinit var fragmentScenario: FragmentScenario<SleepTimerFragment>
  private lateinit var lastEvent: MediaPlayerService.ScheduleAutoSleepEvent

  private fun assertDurationInRange(
    expectedStartSecs: Long,
    expectedEndSecs: Long,
    actualMillis: Long
  ) {
    assertTrue(actualMillis >= expectedStartSecs * 1000L)
    assertTrue(actualMillis < expectedEndSecs * 1000L)
  }

  @Before
  fun setup() {
    mockkObject(InAppReviewFlowManager)
    fragmentScenario = launchFragmentInContainer<SleepTimerFragment>(null, R.style.Theme_App)
    fragmentScenario.onFragment { fragment = it }
    MockKAnnotations.init(this)

    lastEvent = mockk(relaxed = true)
    every {
      eventBus.getStickyEvent(MediaPlayerService.ScheduleAutoSleepEvent::class.java)
    } returns lastEvent
  }

  @Test
  fun testDurationPickerListener_onSleepPreScheduled() {
    every { lastEvent.atUptimeMillis } returns SystemClock.uptimeMillis() + 60 * 1000L
    onView(withId(R.id.duration_picker)).perform(EspressoX.addDurationToPicker(60))
    val eventSlot = slot<MediaPlayerService.ScheduleAutoSleepEvent>()
    verify(exactly = 1) { eventBus.postSticky(capture(eventSlot)) }

    // won't get exact duration back. Need to ensure its in the expected range.
    val remainingDurationMillis = eventSlot.captured.atUptimeMillis - SystemClock.uptimeMillis()
    assertDurationInRange(110, 120, remainingDurationMillis)
  }

  @Test
  fun testDurationPickerListener() {
    onView(withId(R.id.duration_picker)).perform(EspressoX.addDurationToPicker(300))
    val eventSlot = slot<MediaPlayerService.ScheduleAutoSleepEvent>()
    verify(exactly = 1) { eventBus.postSticky(capture(eventSlot)) }

    // won't get exact duration back. Need to ensure its in the expected range.
    val remainingDurationMillis = eventSlot.captured.atUptimeMillis - SystemClock.uptimeMillis()
    assertDurationInRange(290, 300, remainingDurationMillis)
  }

  @Test
  fun testResetButton_shouldNotBeEnabledByDefault() {
    onView(EspressoX.withDurationPickerResetButton(withId(R.id.duration_picker)))
      .check(matches(not(isEnabled())))
  }

  @Test
  fun testResetButton_onAutoSleepScheduled() {
    fragmentScenario.onFragment {
      it.requireView().duration_picker.setResetButtonEnabled(true)
    }

    onView(EspressoX.withDurationPickerResetButton(withId(R.id.duration_picker)))
      .perform(scrollTo(), click())

    onView(withText(R.string.auto_sleep_schedule_cancelled)).check(matches(isDisplayed()))
    verify(exactly = 1) {
      eventBus.postSticky(MediaPlayerService.ScheduleAutoSleepEvent(0))
    }
  }

  @Test
  fun testOnScheduleAutoSleep_onScheduleEvent() {
    fragmentScenario.onFragment {
      it.onScheduleAutoSleep(MediaPlayerService.ScheduleAutoSleepEvent(SystemClock.uptimeMillis() + 999999))
    }

    onView(EspressoX.withDurationPickerResetButton(withId(R.id.duration_picker)))
      .check(matches(isEnabled()))

    onView(withId(R.id.countdown_view)).check(matches(not(withText("00h 00m 00s"))))
    verify(exactly = 1) { InAppReviewFlowManager.maybeAskForReview(any()) }
  }

  @Test
  fun testOnScheduleAutoSleep_onCancelEvent() {
    fragmentScenario.onFragment {
      it.onScheduleAutoSleep(MediaPlayerService.ScheduleAutoSleepEvent(0))
    }

    onView(EspressoX.withDurationPickerResetButton(withId(R.id.duration_picker)))
      .check(matches(not(isEnabled())))

    onView(withId(R.id.countdown_view)).check(matches(withText("00h 00m 00s")))
    verify(exactly = 1) { InAppReviewFlowManager.maybeAskForReview(any()) }
  }
}
