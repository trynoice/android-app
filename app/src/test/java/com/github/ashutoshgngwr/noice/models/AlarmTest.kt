package com.github.ashutoshgngwr.noice.models

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AlarmTest {

  @Test
  fun getTriggerTimeMillis() {
    data class TestCase(
      val alarm: Alarm,
      val currentTimeMillis: Long,
      val expectedTriggerTimeMillis: Long,
    )

    // Thursday, 27 October 2022, 00:00:00.000 Local Timezone
    val baseTimeMillis = Calendar.getInstance()
      .apply {
        set(Calendar.YEAR, 2022)
        set(Calendar.MONTH, Calendar.OCTOBER)
        set(Calendar.DAY_OF_MONTH, 27)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      .timeInMillis

    // In all test cases, the timestamps should be relative to `baseTimeMillis`.
    val testCases = arrayOf(
      TestCase(
        alarm = alarm(540, 0b0),
        currentTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(240),
        expectedTriggerTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(540),
      ),
      TestCase(
        alarm = alarm(240, 0b0),
        currentTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(360),
        expectedTriggerTimeMillis = baseTimeMillis + TimeUnit.DAYS.toMillis(1)
          + TimeUnit.MINUTES.toMillis(240),
      ),
      TestCase(
        alarm = alarm(540, 0b0010000),
        currentTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(360),
        expectedTriggerTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(540),
      ),
      TestCase(
        alarm = alarm(600, 0b0010000),
        currentTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(1200),
        expectedTriggerTimeMillis = baseTimeMillis + TimeUnit.DAYS.toMillis(7)
          + TimeUnit.MINUTES.toMillis(600),
      ),
      TestCase(
        alarm = alarm(600, 0b0010000),
        currentTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(600),
        expectedTriggerTimeMillis = baseTimeMillis + TimeUnit.DAYS.toMillis(7)
          + TimeUnit.MINUTES.toMillis(600),
      ),
      TestCase(
        alarm = alarm(600, 0b1110000),
        currentTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(1200),
        expectedTriggerTimeMillis = baseTimeMillis + TimeUnit.DAYS.toMillis(1)
          + TimeUnit.MINUTES.toMillis(600),
      ),
      TestCase(
        alarm = alarm(600, 0b0010001),
        currentTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(1200),
        expectedTriggerTimeMillis = baseTimeMillis + TimeUnit.DAYS.toMillis(3)
          + TimeUnit.MINUTES.toMillis(600),
      ),
      TestCase(
        alarm = alarm(600, 0b0010100),
        currentTimeMillis = baseTimeMillis + TimeUnit.MINUTES.toMillis(1200),
        expectedTriggerTimeMillis = baseTimeMillis + TimeUnit.DAYS.toMillis(5)
          + TimeUnit.MINUTES.toMillis(600),
      ),
    )

    testCases.forEachIndexed { index, testCase ->
      assertEquals(
        "Testcase #$index failed",
        testCase.expectedTriggerTimeMillis,
        testCase.alarm.getTriggerTimeMillis(testCase.currentTimeMillis),
      )
    }
  }

  private fun alarm(minuteOfDay: Int, weeklySchedule: Int): Alarm {
    return Alarm(
      id = 0,
      label = null,
      minuteOfDay = minuteOfDay,
      isEnabled = true,
      weeklySchedule = weeklySchedule,
      vibrate = true,
      preset = null,
    )
  }
}
