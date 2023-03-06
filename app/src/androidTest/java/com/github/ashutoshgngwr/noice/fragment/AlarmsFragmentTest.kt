package com.github.ashutoshgngwr.noice.fragment

import android.provider.Settings
import android.widget.EditText
import androidx.paging.PagingData
import androidx.test.espresso.AmbiguousViewMatcherException
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import com.github.ashutoshgngwr.noice.EspressoX.clickOn
import com.github.ashutoshgngwr.noice.EspressoX.itemAtPosition
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.di.AlarmRepositoryModule
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.hamcrest.Matchers.*
import org.hamcrest.core.AllOf.allOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(AlarmRepositoryModule::class)
class AlarmsFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var alarmRepositoryMock: AlarmRepository

  @BindValue
  internal lateinit var presetRepositoryMock: PresetRepository

  @Before
  fun setUp() {
    alarmRepositoryMock = mockk(relaxed = true)
    presetRepositoryMock = mockk(relaxed = true)
  }

  @Test
  fun emptyListIndicator() {
    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.empty())
    launchFragmentInHiltContainer<AlarmsFragment>().use {
      onView(withId(R.id.empty_list_indicator))
        .check(matches(isDisplayed()))
    }

    every {
      alarmRepositoryMock.pagingDataFlow()
    } returns flowOf(PagingData.from(listOf(buildAlarm(id = 1))))

    launchFragmentInHiltContainer<AlarmsFragment>().use {
      onView(withId(R.id.empty_list_indicator))
        .check(matches(not(isDisplayed())))
    }
  }

  @Test
  fun list() {
    val presets = listOf(
      Preset("preset-1", arrayOf()),
      Preset("preset-2", arrayOf()),
      Preset("preset-3", arrayOf()),
      Preset("preset-4", arrayOf()),
    )

    // keep `minuteOfDay` larger than 1 hour and smaller than 12 hours to avoid 24 hour date format issues.
    val alarms = listOf(
      buildAlarm(id = 1, minuteOfDay = 60, label = "alarm-1", vibrate = true),
      buildAlarm(id = 2, minuteOfDay = 120, isEnabled = true, preset = presets.random()),
      buildAlarm(id = 3, minuteOfDay = 180, vibrate = true),
      buildAlarm(id = 4, minuteOfDay = 240, weeklySchedule = 0b1010101, preset = presets.random()),
    )

    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(alarms))
    every { presetRepositoryMock.listFlow() } returns flowOf(presets)
    launchFragmentInHiltContainer<AlarmsFragment>()


    onView(withId(R.id.list))
      .check(matches(isDisplayed()))
      // collapse the item expanded by default
      .perform(actionOnItemAtPosition<AlarmViewHolder>(0, clickOn(R.id.expand_toggle)))

    alarms.forEachIndexed { index, alarm ->
      val labelMatcher = allOf(isDisplayed(), withText(alarm.label ?: ""))
      val enableSwitchMatcher = allOf(
        isDisplayed(),
        if (alarm.isEnabled) isChecked() else isNotChecked(),
      )

      val hour = (alarm.minuteOfDay / 60).toString()
      val minute = (alarm.minuteOfDay % 60).toString().padStart(2, '0')
      val timeMatcher = allOf(isDisplayed(), withText(containsString("${hour}:${minute}")))
      val nextTriggerMatcher = allOf(
        isDisplayed(),
        if (alarm.isEnabled) not(withText(R.string.not_scheduled)) else withText(R.string.not_scheduled),
      )

      val presetMatcher = allOf(
        isDisplayed(),
        if (alarm.preset == null) withText(R.string.random_preset) else withText(alarm.preset?.name),
      )

      val vibrateMatcher = allOf(isDisplayed(), if (alarm.vibrate) isChecked() else isNotChecked())

      onView(withId(R.id.list))
        .perform(scrollToPosition<AlarmViewHolder>(index)) // ensure view holder exists
        // checks when item is collapsed
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.label,
            assertion = matches(if (alarm.label == null) not(isDisplayed()) else labelMatcher),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.enable_switch,
            assertion = matches(enableSwitchMatcher),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.time,
            assertion = matches(timeMatcher),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.next_trigger,
            assertion = matches(nextTriggerMatcher),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.schedule_toggle_container,
            assertion = matches(not(isDisplayed())),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.preset,
            assertion = matches(not(isDisplayed())),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.vibrate,
            assertion = matches(not(isDisplayed())),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.delete,
            assertion = matches(not(isDisplayed())),
          )
        )

      // expand this item and perform more checks.
      onView(withId(R.id.list))
        .perform(actionOnItemAtPosition<AlarmViewHolder>(index, clickOn(R.id.expand_toggle)))

      // add an explicit break in the view interaction chain due to intermittent
      // NoMatchingViewExceptions on performing the last action.
      onView(withId(R.id.list))
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.label,
            assertion = matches(labelMatcher)
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.schedule_toggle_container,
            assertion = matches(isDisplayed()),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.preset,
            assertion = matches(presetMatcher),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.vibrate,
            assertion = matches(vibrateMatcher),
          )
        )
        .check(
          itemAtPosition(
            position = index,
            viewId = R.id.delete,
            assertion = matches(isDisplayed()),
          )
        )

      mapOf(
        R.id.sunday_toggle to (1 shl 0),
        R.id.monday_toggle to (1 shl 1),
        R.id.tuesday_toggle to (1 shl 2),
        R.id.wednesday_toggle to (1 shl 3),
        R.id.thursday_toggle to (1 shl 4),
        R.id.friday_toggle to (1 shl 5),
        R.id.saturday_toggle to (1 shl 6),
      ).forEach { (viewId, bitMask) ->
        onView(withId(R.id.list))
          .check(
            itemAtPosition(
              position = index,
              viewId = viewId,
              assertion = matches(
                allOf(
                  isDisplayed(),
                  if (alarm.weeklySchedule and bitMask != 0) isChecked() else isNotChecked()
                )
              )
            )
          )
      }
    }
  }

  @Test
  fun focusedAlarmIdNavArg() {
    val alarms = listOf(
      buildAlarm(id = 0, minuteOfDay = 60),
      buildAlarm(id = 1, minuteOfDay = 120),
      buildAlarm(id = 2, minuteOfDay = 180),
      buildAlarm(id = 3, minuteOfDay = 240),
    )

    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(alarms))
    launchFragmentInHiltContainer<AlarmsFragment>(
      fragmentArgs = AlarmsFragmentArgs(3).toBundle(),
    )

    onView(withId(R.id.list))
      .check(matches(isDisplayed()))
      .perform(scrollToPosition<AlarmViewHolder>(0))
      .check(itemAtPosition(0, R.id.delete, matches(not(isDisplayed()))))
      .perform(scrollToPosition<AlarmViewHolder>(3))
      .check(itemAtPosition(3, R.id.delete, matches(isDisplayed())))
  }

  @Test
  fun addAlarm() {
    every { alarmRepositoryMock.canScheduleAlarms() } returns false
    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.empty())
    launchFragmentInHiltContainer<AlarmsFragment>().use {
      onView(withId(R.id.add_alarm_button))
        .check(matches(isDisplayed()))
        .perform(click())

      onView(withText(R.string.alarm_permission_rationale_title))
        .inRoot(isDialog())
        .check(matches(isDisplayed()))

      try {
        Intents.init()
        onView(withText(R.string.settings))
          .inRoot(isDialog())
          .perform(click())

        Intents.intended(hasAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
      } finally {
        Intents.release()
      }
    }

    every { alarmRepositoryMock.canScheduleAlarms() } returns true
    launchFragmentInHiltContainer<AlarmsFragment>().use {
      onView(withId(R.id.add_alarm_button))
        .check(matches(isDisplayed()))
        .perform(click())

      setAmTimeInTimePickerDialog(2, 20)
      coVerify(exactly = 1, timeout = 5000) {
        alarmRepositoryMock.save(withArg { actual ->
          assertEquals(140, actual.minuteOfDay)
        })
      }
    }
  }

  @Test
  fun updateLabel() {
    val alarm = buildAlarm(id = 1, label = "test-label")
    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(listOf(alarm)))
    launchFragmentInHiltContainer<AlarmsFragment>()
    onView(withText(alarm.label))
      .check(matches(isDisplayed()))
      .perform(click())

    val newLabel = "new-test-label"
    onView(isAssignableFrom(EditText::class.java))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(replaceText(newLabel))

    onView(withId(R.id.positive))
      .inRoot(isDialog())
      .perform(click())

    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.save(withArg { actual ->
        assertEquals(newLabel, actual.label)
      })
    }
  }

  @Test
  fun updateTime() {
    val alarm = buildAlarm(id = 1)
    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(listOf(alarm)))
    launchFragmentInHiltContainer<AlarmsFragment>()

    every { alarmRepositoryMock.canScheduleAlarms() } returns true
    onView(withId(R.id.time))
      .check(matches(isDisplayed()))
      .perform(click())

    setAmTimeInTimePickerDialog(3, 30)
    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.save(withArg { actual ->
        assertEquals(210, actual.minuteOfDay)
        assertEquals(true, actual.isEnabled)
      })
    }

    every { alarmRepositoryMock.canScheduleAlarms() } returns false
    onView(withId(R.id.time))
      .check(matches(isDisplayed()))
      .perform(click())

    setAmTimeInTimePickerDialog(4, 40)
    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.save(withArg { actual ->
        assertEquals(280, actual.minuteOfDay)
        assertEquals(false, actual.isEnabled)
      })
    }
  }

  @Test
  fun enableToggle() {
    every { alarmRepositoryMock.canScheduleAlarms() } returns true
    val alarm = buildAlarm(id = 1, isEnabled = false)
    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(listOf(alarm)))
    launchFragmentInHiltContainer<AlarmsFragment>()

    onView(withId(R.id.enable_switch))
      .check(matches(isDisplayed()))
      .check(matches(isNotChecked()))
      .perform(click())

    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.save(withArg { actual ->
        assertTrue(actual.isEnabled)
      })
    }

    onView(withId(R.id.enable_switch))
      .check(matches(isChecked()))
      .perform(click())

    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.save(withArg { actual ->
        assertFalse(actual.isEnabled)
      })
    }
  }

  @Test
  fun updateWeeklySchedule() {
    val alarm = buildAlarm(id = 1, isEnabled = true)
    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(listOf(alarm)))
    launchFragmentInHiltContainer<AlarmsFragment>()

    mapOf(
      R.id.sunday_toggle to (1 shl 0),
      R.id.monday_toggle to (1 shl 1),
      R.id.tuesday_toggle to (1 shl 2),
      R.id.wednesday_toggle to (1 shl 3),
      R.id.thursday_toggle to (1 shl 4),
      R.id.friday_toggle to (1 shl 5),
      R.id.saturday_toggle to (1 shl 6),
    ).forEach { (toggleId, bitMask) ->
      clearMocks(alarmRepositoryMock)
      onView(withId(toggleId))
        .check(matches(isDisplayed()))
        .check(matches(not(isChecked())))
        .perform(click())

      coVerify(exactly = 1, timeout = 5000) {
        alarmRepositoryMock.save(withArg { actual ->
          assertNotEquals(0, actual.weeklySchedule and bitMask)
        })
      }

      onView(withId(toggleId))
        .check(matches(isChecked()))
        .perform(click())

      coVerify(exactly = 1, timeout = 5000) {
        alarmRepositoryMock.save(withArg { actual ->
          assertEquals(0, actual.weeklySchedule and bitMask)
        })
      }
    }
  }

  @Test
  fun updatePreset() {
    val alarm = buildAlarm(id = 1, preset = null)
    val presets = listOf(
      Preset("preset-1", arrayOf()),
      Preset("preset-3", arrayOf()),
      Preset("preset-2", arrayOf()),
    )

    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(listOf(alarm)))
    every { presetRepositoryMock.listFlow() } returns flowOf(presets)
    launchFragmentInHiltContainer<AlarmsFragment>()

    onView(withText(R.string.random_preset))
      .check(matches(isDisplayed()))
      .perform(click())

    val chosen = presets.random()
    onView(withText(chosen.name))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(click())

    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.save(withArg { actual ->
        assertEquals(chosen, actual.preset)
      })
    }
  }

  @Test
  fun vibrateToggle() {
    val alarm = buildAlarm(id = 1, preset = null)
    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(listOf(alarm)))
    launchFragmentInHiltContainer<AlarmsFragment>()

    onView(withId(R.id.vibrate))
      .check(matches(isDisplayed()))
      .check(matches(isNotChecked()))
      .perform(click())

    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.save(withArg { actual ->
        assertTrue(actual.vibrate)
      })
    }

    onView(withId(R.id.vibrate))
      .check(matches(isChecked()))
      .perform(click())

    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.save(withArg { actual ->
        assertFalse(actual.vibrate)
      })
    }
  }

  @Test
  fun delete() {
    val alarm = buildAlarm(id = 1, preset = null)
    every { alarmRepositoryMock.pagingDataFlow() } returns flowOf(PagingData.from(listOf(alarm)))
    launchFragmentInHiltContainer<AlarmsFragment>()

    onView(withId(R.id.delete))
      .check(matches(isDisplayed()))
      .perform(click())

    coVerify(exactly = 1, timeout = 5000) {
      alarmRepositoryMock.delete(alarm)
    }
  }

  private fun setAmTimeInTimePickerDialog(hour: Int, minute: Int) {
    onView(withId(com.google.android.material.R.id.material_hour_tv))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(click())

    try {
      onView(withText(hour.toString()))
        .inRoot(isDialog())
        .perform(click())
    } catch (e: AmbiguousViewMatcherException) { // value may already be set to the desired hour
      onView(withId(com.google.android.material.R.id.material_hour_tv))
        .inRoot(isDialog())
        .check(matches(withText(hour.toString())))
    }

    onView(withId(com.google.android.material.R.id.material_minute_tv))
      .inRoot(isDialog())
      .perform(click())

    try {
      onView(withText(minute.toString()))
        .inRoot(isDialog())
        .perform(click())
    } catch (e: AmbiguousViewMatcherException) { // value may already be set to the desired minute
      onView(withId(com.google.android.material.R.id.material_minute_tv))
        .inRoot(isDialog())
        .check(matches(withText(minute.toString())))
    }

    try {
      onView(withId(com.google.android.material.R.id.material_clock_period_am_button))
        .inRoot(isDialog())
        .perform(click())
    } catch (e: NoMatchingViewException) {
      // the device may be using 24-hour time format.
    }

    onView(withId(com.google.android.material.R.id.material_timepicker_ok_button))
      .inRoot(isDialog())
      .perform(click())
  }

  private fun buildAlarm(
    id: Int,
    label: String? = null,
    isEnabled: Boolean = false,
    minuteOfDay: Int = 0,
    weeklySchedule: Int = 0,
    preset: Preset? = null,
    vibrate: Boolean = false
  ): Alarm {
    return Alarm(
      id = id,
      label = label,
      isEnabled = isEnabled,
      minuteOfDay = minuteOfDay,
      weeklySchedule = weeklySchedule,
      preset = preset,
      vibrate = vibrate,
    )
  }
}
