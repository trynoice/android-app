package com.github.ashutoshgngwr.noice

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.Utils.withGson
import com.github.ashutoshgngwr.noice.sound.Preset
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(RobolectricTestRunner::class)
class WakeUpTimerManagerTest {

  private lateinit var mockPrefs: SharedPreferences
  private lateinit var shadowAlarmManager: ShadowAlarmManager

  @Before
  fun setup() {
    mockkStatic(PreferenceManager::class)
    mockPrefs = mockk(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    shadowAlarmManager = shadowOf(
      ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(AlarmManager::class.java)
    )
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testSet() {
    val expectedTime = System.currentTimeMillis() + 10000L
    val expectedPresetID = "test-preset-id"
    val expectedVolume = 10
    mockkObject(Preset.Companion)

    for (shouldUpdateMediaVolume in arrayOf(true, false)) {
      ShadowPendingIntent.reset()
      val expectedTimer = WakeUpTimerManager.Timer(
        expectedPresetID, expectedTime, shouldUpdateMediaVolume, expectedVolume
      )

      val expectedJSON = withGson { it.toJson(expectedTimer) }
      val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
        every { putString(any(), any()) } returns this
      }

      every { mockPrefs.edit() } returns mockPrefsEditor
      every { Preset.findByID(any(), expectedPresetID) } returns mockk()

      WakeUpTimerManager.set(ApplicationProvider.getApplicationContext(), expectedTimer)

      verifyOrder {
        mockPrefs.edit()
        mockPrefsEditor.putString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, expectedJSON)
        mockPrefsEditor.apply()
      }

      val alarm = shadowAlarmManager.nextScheduledAlarm
      val i = shadowOf(alarm.operation).savedIntent
      assertEquals(expectedTime, alarm.triggerAtTime)
      assertEquals(expectedPresetID, i.getStringExtra(MediaPlayerService.EXTRA_PRESET_ID))

      val vol = i.getIntExtra(MediaPlayerService.EXTRA_DEVICE_MEDIA_VOLUME, -1)
      if (shouldUpdateMediaVolume) {
        assertEquals(expectedVolume, vol)
      } else {
        assertEquals(-1, vol)
      }
    }
  }

  @Test
  fun testCancel() {
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { remove(any()) } returns this
    }

    every { mockPrefs.edit() } returns mockPrefsEditor
    WakeUpTimerManager.cancel(ApplicationProvider.getApplicationContext())

    verifyOrder {
      mockPrefs.edit()
      mockPrefsEditor.remove(WakeUpTimerManager.PREF_WAKE_UP_TIMER)
      mockPrefsEditor.apply()
    }

    assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
  }

  @Test
  fun testGet() {
    // when timer is not scheduled
    every { mockPrefs.getString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, any()) } returns null
    assertNull(WakeUpTimerManager.get(ApplicationProvider.getApplicationContext()))

    every {
      mockPrefs.getString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, any())
    } returns """{"presetID": "test", "atMillis": 1, "mediaVolume": 10}"""

    val timer = WakeUpTimerManager.get(ApplicationProvider.getApplicationContext())
    assertEquals(1L, timer?.atMillis)
    assertEquals("test", timer?.presetID)
    assertEquals(10, timer?.mediaVolume)
  }

  @Test
  fun testBootReceiver_whenTimerIsPreScheduled() {
    val expectedTime = System.currentTimeMillis() + 1000L
    every {
      mockPrefs.getString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, any())
    } returns """{"presetID": "test", "atMillis": $expectedTime}"""

    mockkObject(Preset.Companion)
    every { Preset.findByID(any(), "test") } returns mockk()

    WakeUpTimerManager.BootReceiver()
      .onReceive(ApplicationProvider.getApplicationContext(), Intent(Intent.ACTION_BOOT_COMPLETED))

    assertEquals(expectedTime, shadowAlarmManager.nextScheduledAlarm.triggerAtTime)
  }

  @Test
  fun testBootReceiver_whenTimeIsNotPreScheduled() {
    every { mockPrefs.getString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, any()) } returns null
    WakeUpTimerManager.BootReceiver()
      .onReceive(ApplicationProvider.getApplicationContext(), Intent(Intent.ACTION_BOOT_COMPLETED))

    assertNull(shadowAlarmManager.nextScheduledAlarm)
  }

  @Test
  fun testSaveLastUsedPresetID() {
    val presetID = "test-preset-id"
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putString(any(), any()) } returns this
    }

    every { mockPrefs.edit() } returns mockPrefsEditor
    WakeUpTimerManager.saveLastUsedPresetID(ApplicationProvider.getApplicationContext(), presetID)
    verify(exactly = 1) {
      mockPrefs.edit()
      mockPrefsEditor.putString(WakeUpTimerManager.PREF_LAST_USED_PRESET_ID, presetID)
      mockPrefsEditor.apply()
    }
  }

  @Test
  fun testGetLastUsedPresetID() {
    mockkObject(Preset.Companion)

    val context = ApplicationProvider.getApplicationContext<Context>()
    every { mockPrefs.getString(WakeUpTimerManager.PREF_LAST_USED_PRESET_ID, any()) } returns null
    assertNull(WakeUpTimerManager.getLastUsedPresetID(context))

    val presetID = "test-preset-id"
    every {
      mockPrefs.getString(WakeUpTimerManager.PREF_LAST_USED_PRESET_ID, any())
    } returns presetID

    every { Preset.findByID(any(), presetID) } returns null // preset doesn't exist
    assertNull(WakeUpTimerManager.getLastUsedPresetID(context))

    every { Preset.findByID(any(), presetID) } returns mockk {
      every { id } returns presetID
    }

    assertEquals(presetID, WakeUpTimerManager.getLastUsedPresetID(context))
  }

  @Test
  fun testGetStringScheduleLeftTime_returnStringMinute(){
    val context = ApplicationProvider.getApplicationContext<Context>()
    val stringMinute1 = WakeUpTimerManager.getStringScheduleLeftTime(context, 0, 0)
    val stringMinute2 = WakeUpTimerManager.getStringScheduleLeftTime(context, 0, 1)

    val stringShouldDisplay1 = context.getString(R.string.wake_up_timer_schedule_set_minute,0)
    val stringShouldDisplay2 = context.getString(R.string.wake_up_timer_schedule_set_minute,1)

    assertEquals(stringShouldDisplay1, stringMinute1)
    assertEquals(stringShouldDisplay2, stringMinute2)
  }

  @Test
  fun testGetStringScheduleLeftTime_returnStringHour(){
    val context = ApplicationProvider.getApplicationContext<Context>()
    val stringHour = WakeUpTimerManager.getStringScheduleLeftTime(context, 1, 0)
    val stringShouldDisplay = context.getString(R.string.wake_up_timer_schedule_set_hour,1)

    assertEquals(stringShouldDisplay, stringHour)
  }

  @Test
  fun testGetStringScheduleLeftTime_returnStringHourAndMinute(){
    val context = ApplicationProvider.getApplicationContext<Context>()
    val stringHourAndMinute = WakeUpTimerManager.getStringScheduleLeftTime(context, 1, 1)
    val stringShouldDisplay = context.getString(R.string.wake_up_timer_schedule_set_hour_minute,1, 1)

    assertEquals(stringShouldDisplay, stringHourAndMinute)
  }

  @Test
  fun testGetStringScheduleLeftTime_returnStringHourAndMinutes(){
    val context = ApplicationProvider.getApplicationContext<Context>()
    val stringHourAndMinutes = WakeUpTimerManager.getStringScheduleLeftTime(context, 1, 2)
    val stringShouldDisplay = context.getString(R.string.wake_up_timer_schedule_set_hour_minutes,1, 2)

    assertEquals(stringShouldDisplay, stringHourAndMinutes)
  }

  @Test
  fun testGetStringScheduleLeftTime_returnStringHours(){
    val context = ApplicationProvider.getApplicationContext<Context>()
    val stringHours = WakeUpTimerManager.getStringScheduleLeftTime(context, 2, 0)
    val stringShouldDisplay = context.getString(R.string.wake_up_timer_schedule_set_hours,2, 0)

    assertEquals(stringShouldDisplay, stringHours)
  }

  @Test
  fun testGetStringScheduleLeftTime_returnStringHoursAndMinute(){
    val context = ApplicationProvider.getApplicationContext<Context>()
    val stringHoursAndMinute = WakeUpTimerManager.getStringScheduleLeftTime(context, 2, 1)
    val stringShouldDisplay = context.getString(R.string.wake_up_timer_schedule_set_hours_minute,2, 1)

    assertEquals(stringShouldDisplay, stringHoursAndMinute)
  }

  @Test
  fun testGetStringScheduleLeftTime_returnStringHoursAndMinutes(){
    val context = ApplicationProvider.getApplicationContext<Context>()
    val stringHoursAndMinutes = WakeUpTimerManager.getStringScheduleLeftTime(context, 2, 2)
    val stringShouldDisplay = context.getString(R.string.wake_up_timer_schedule_set_hours_minutes,2, 2)

    assertEquals(stringShouldDisplay, stringHoursAndMinutes)
  }
}
