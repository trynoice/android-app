package com.github.ashutoshgngwr.noice.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.BundleMatchers.*
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.di.AlarmRepositoryModule
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.random.Random

@HiltAndroidTest
@UninstallModules(AlarmRepositoryModule::class)
class SetAlarmHandlerActivityTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var alarmRepositoryMock: AlarmRepository

  @Before
  fun setUp() {
    // initialise work manager with a no-op executor
    val context = ApplicationProvider.getApplicationContext<Context>()
    Configuration.Builder()
      .setMinimumLoggingLevel(Log.ERROR)
      .setExecutor { } // no-op
      .build()
      .also { WorkManagerTestInitHelper.initializeTestWorkManager(context, it) }

    alarmRepositoryMock = mockk(relaxed = true)
  }

  @Test
  fun setAlarmIntent() {
    data class TestCase(
      val setAlarmIntent: Intent,
      val expectedAlarm: Alarm?,
      val shouldLaunchAlarmsFragment: Boolean,
    )

    val context = ApplicationProvider.getApplicationContext<Context>()
    arrayOf(
      TestCase(
        setAlarmIntent = Intent(context, SetAlarmHandlerActivity::class.java),
        expectedAlarm = null,
        shouldLaunchAlarmsFragment = false,
      ),
      TestCase(
        setAlarmIntent = buildSetAlarmIntent(hour = null, minute = null),
        expectedAlarm = null,
        shouldLaunchAlarmsFragment = true,
      ),
      TestCase(
        setAlarmIntent = buildSetAlarmIntent(hour = 1, minute = 10),
        expectedAlarm = buildAlarm(minuteOfDay = 70),
        shouldLaunchAlarmsFragment = true,
      ),
      TestCase(
        setAlarmIntent = buildSetAlarmIntent(
          hour = 2,
          minute = 20,
          days = arrayListOf(
            Calendar.SUNDAY,
            Calendar.TUESDAY,
            Calendar.THURSDAY,
            Calendar.SATURDAY,
          ),
          vibrate = false,
          message = "test-label",
        ),
        expectedAlarm = buildAlarm(
          minuteOfDay = 140,
          weeklySchedule = 0b1010101,
          label = "test-label",
          vibrate = false,
        ),
        shouldLaunchAlarmsFragment = true,
      ),
      TestCase(
        setAlarmIntent = buildSetAlarmIntent(hour = 4, minute = 0)
          .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
        expectedAlarm = buildAlarm(minuteOfDay = 240),
        shouldLaunchAlarmsFragment = false,
      ),
    ).forEach { testCase ->
      try {
        Intents.init()
        val savedAlarmId = Random.nextInt()
        coEvery { alarmRepositoryMock.save(any()) } returns savedAlarmId
        launch<SetAlarmHandlerActivity>(testCase.setAlarmIntent).use {
          coVerify(exactly = if (testCase.expectedAlarm != null) 1 else 0, timeout = 5000) {
            alarmRepositoryMock.save(withArg { actual ->
              assertEquals(testCase.expectedAlarm, actual)
            })
          }

          Intents.intended(
            allOf(
              hasComponent(ComponentName(context, MainActivity::class.java)),
              hasExtra(MainActivity.EXTRA_HOME_DESTINATION, R.id.alarms),
              if (testCase.expectedAlarm != null)
                hasExtra(
                  `is`(MainActivity.EXTRA_HOME_DESTINATION_ARGS),
                  hasEntry("focused_alarm_id", savedAlarmId),
                )
              else
                not(hasExtraWithKey(MainActivity.EXTRA_HOME_DESTINATION_ARGS)),
            ),
            Intents.times(if (testCase.shouldLaunchAlarmsFragment) 1 else 0),
          )
        }
      } finally {
        Intents.release()
        clearMocks(alarmRepositoryMock)
      }
    }
  }

  private fun buildSetAlarmIntent(
    hour: Int?,
    minute: Int?,
    days: ArrayList<Int>? = null,
    message: String? = null,
    vibrate: Boolean? = null,
  ): Intent {
    return Intent(ApplicationProvider.getApplicationContext(), SetAlarmHandlerActivity::class.java)
      .setAction(AlarmClock.ACTION_SET_ALARM)
      .apply { if (hour != null) putExtra(AlarmClock.EXTRA_HOUR, hour) }
      .apply { if (minute != null) putExtra(AlarmClock.EXTRA_MINUTES, minute) }
      .apply { if (days != null) putExtra(AlarmClock.EXTRA_DAYS, days) }
      .apply { if (vibrate != null) putExtra(AlarmClock.EXTRA_VIBRATE, vibrate) }
      .apply { if (message != null) putExtra(AlarmClock.EXTRA_MESSAGE, message) }
  }

  private fun buildAlarm(
    minuteOfDay: Int,
    weeklySchedule: Int = 0,
    label: String? = null,
    vibrate: Boolean = true,
  ): Alarm {
    return Alarm(
      id = 0,
      label = label,
      isEnabled = true,
      minuteOfDay = minuteOfDay,
      weeklySchedule = weeklySchedule,
      preset = null,
      vibrate = vibrate,
    )
  }
}
