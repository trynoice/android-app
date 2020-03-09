package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions.setTime
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.R
import kotlinx.android.synthetic.main.fragment_sleep_timer.*
import kotlinx.android.synthetic.main.fragment_sleep_timer.view.*
import org.greenrobot.eventbus.EventBus
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class SleepTimerFragmentTest {

  @Mock
  private lateinit var eventBus: EventBus

  @InjectMocks
  private lateinit var fragment: SleepTimerFragment

  private lateinit var fragmentScenario: FragmentScenario<SleepTimerFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInContainer<SleepTimerFragment>(null, R.style.AppTheme)
    fragmentScenario.onFragment {
      fragment = it
    }

    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun testScheduleButton_withInvalidInput() {
    onView(withId(R.id.button_schedule)).perform(scrollTo(), click())
    onView(withText(R.string.auto_sleep_schedule_error)).check(matches(isDisplayed()))
    verify(eventBus, atMost(0)).postSticky(any())
  }

  @Test
  fun testScheduleButton_withValidInput() {
    fragmentScenario.onFragment {
      it.requireView().time_picker.minute = 1
    }

    onView(withId(R.id.button_schedule)).perform(scrollTo(), click())
    onView(withText(R.string.auto_sleep_schedule_success)).check(matches(isDisplayed()))
    verify(eventBus, atMost(1))
      .postSticky(any())
  }

  @Test
  fun testResetButton_shouldNotBeEnabledByDefault() {
    onView(withId(R.id.button_reset)).check(matches(not(isEnabled())))
  }

  @Test
  fun testResetButton_onAutoSleepScheduled() {
    fragmentScenario.onFragment {
      it.requireView().button_reset.isEnabled = true
    }

    onView(withId(R.id.button_reset)).perform(scrollTo(), click())
    onView(withText(R.string.auto_sleep_schedule_cancelled)).check(matches(isDisplayed()))
    verify(eventBus, atMostOnce()).postSticky(SleepTimerFragment.ScheduleAutoSleepEvent(0))
  }

  @Test
  fun testOnScheduleAutoSleep_onScheduleEvent() {
    fragmentScenario.onFragment {
      it.onScheduleAutoSleep(SleepTimerFragment.ScheduleAutoSleepEvent(SystemClock.uptimeMillis() + 999999))
    }

    onView(withId(R.id.button_reset)).check(matches(isEnabled()))
    fragmentScenario.onFragment {
      assertTrue(it.time_picker.minute > 0)
    }
  }

  @Test
  fun testOnScheduleAutoSleep_onCancelEvent() {
    fragmentScenario.onFragment {
      it.onScheduleAutoSleep(SleepTimerFragment.ScheduleAutoSleepEvent(0))
    }

    onView(withId(R.id.button_reset)).check(matches(not(isEnabled())))
    fragmentScenario.onFragment {
      assertEquals(0, it.time_picker.minute)
    }
  }

  @Test
  fun testOnSaveInstanceState() {
    onView(withId(R.id.time_picker)).perform(setTime(1, 1))

    val outState = Bundle()
    fragmentScenario.onFragment { it.onSaveInstanceState(outState) }
    assertEquals(1, outState.getInt("hour"))
    assertEquals(1, outState.getInt("minute"))
  }
}
