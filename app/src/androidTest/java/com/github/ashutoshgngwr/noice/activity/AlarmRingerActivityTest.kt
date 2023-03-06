package com.github.ashutoshgngwr.noice.activity

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.launchActivityForResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.di.AlarmRingerModule
import com.github.ashutoshgngwr.noice.service.AlarmRingerService
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(AlarmRingerModule::class)
class AlarmRingerActivityTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var serviceController: AlarmRingerActivity.ServiceController

  @BindValue
  internal lateinit var uiController: AlarmRingerService.UiController

  private lateinit var activityScenario: ActivityScenario<AlarmRingerActivity>

  @Before
  fun setup() {
    serviceController = mockk()
    uiController = mockk()
    activityScenario = launchActivityForResult(
      AlarmRingerActivity.buildIntent(
        context = ApplicationProvider.getApplicationContext(),
        alarmId = ALARM_ID,
        alarmLabel = ALARM_LABEL,
        alarmTriggerTime = ALARM_TRIGGER_TIME,
      )
    )
  }

  @Test
  fun alarmTriggerTimeAndLabel() {
    onView(withId(R.id.label))
      .check(matches(isDisplayed()))
      .check(matches(withText(ALARM_LABEL)))

    onView(withId(R.id.trigger_time))
      .check(matches(isDisplayed()))
      .check(matches(withText(ALARM_TRIGGER_TIME)))
  }

  @Test
  fun dismiss() {
    every { serviceController.dismiss(any()) } returns Unit
    onView(withId(R.id.dismiss))
      .check(matches(isDisplayed()))
      .perform(click())

    verify(exactly = 1, timeout = 5000L) { serviceController.dismiss(ALARM_ID) }
  }

  @Test
  fun snooze() {
    every { serviceController.snooze(any()) } returns Unit
    onView(withId(R.id.snooze))
      .check(matches(isDisplayed()))
      .perform(click())

    verify(exactly = 1, timeout = 5000L) { serviceController.snooze(ALARM_ID) }
  }

  @Test
  fun dismissActivity() {
    AlarmRingerActivity.dismiss(ApplicationProvider.getApplicationContext())
    Assert.assertEquals(Activity.RESULT_CANCELED, activityScenario.result?.resultCode)
  }

  companion object {
    private const val ALARM_ID = 1
    private const val ALARM_LABEL = "test_alarm_label"
    private const val ALARM_TRIGGER_TIME = "Sunday, 01:00 PM"
  }
}
