package com.github.ashutoshgngwr.noice

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.Utils.withGson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
class WakeUpTimerManagerTest {

  private lateinit var mockContext: Context
  private lateinit var mockPrefs: SharedPreferences
  private lateinit var shadowAlarmManager: ShadowAlarmManager

  @Before
  fun setup() {
    mockkStatic(PreferenceManager::class)
    mockPrefs = mockk(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

    val alarmManager = RuntimeEnvironment.systemContext.getSystemService(AlarmManager::class.java)
    shadowAlarmManager = shadowOf(alarmManager)
    mockContext = mockk(relaxed = true) {
      every { getSystemService(AlarmManager::class.java) } returns alarmManager
    }
  }

  @Test
  fun testSet() {
    val expectedTriggerTime = System.currentTimeMillis() + 10000L
    val expectedTimer = WakeUpTimerManager.Timer("test", expectedTriggerTime)
    val expectedJSON = withGson { it.toJson(expectedTimer) }
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putString(any(), any()) } returns this
    }

    every { mockPrefs.edit() } returns mockPrefsEditor
    WakeUpTimerManager.set(mockContext, expectedTimer)

    verifyOrder {
      mockPrefs.edit()
      mockPrefsEditor.putString(any(), expectedJSON)
      mockPrefsEditor.apply()
    }

    assertEquals(expectedTriggerTime, shadowAlarmManager.nextScheduledAlarm.triggerAtTime)
  }

  @Test
  fun testCancel() {
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { remove(any()) } returns this
    }

    every { mockPrefs.edit() } returns mockPrefsEditor
    WakeUpTimerManager.cancel(mockContext)

    verifyOrder {
      mockPrefs.edit()
      mockPrefsEditor.remove(any())
      mockPrefsEditor.apply()
    }

    assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
  }

  @Test
  fun testGet() {
    // when timer is not scheduled
    every { mockPrefs.getString(any(), any()) } returns null
    assertNull(WakeUpTimerManager.get(mockContext))

    every { mockPrefs.getString(any(), any()) } returns "{\"presetName\":\"test\", \"atMillis\": 1}"
    val timer = WakeUpTimerManager.get(mockContext)
    assertEquals(1L, timer?.atMillis)
    assertEquals("test", timer?.presetName)
  }

  @Test
  fun testBootReceiver_whenTimerIsPreScheduled() {
    val expectedTime = System.currentTimeMillis() + 1000L
    every {
      mockPrefs.getString(any(), any())
    } returns "{\"presetName\":\"test\", \"atMillis\": $expectedTime}"

    WakeUpTimerManager.BootReceiver().onReceive(mockContext, Intent(Intent.ACTION_BOOT_COMPLETED))
    assertEquals(expectedTime, shadowAlarmManager.nextScheduledAlarm.triggerAtTime)
  }

  @Test
  fun testBootReceiver_whenTimeIsNotPreScheduled() {
    every { mockPrefs.getString(any(), any()) } returns null
    WakeUpTimerManager.BootReceiver().onReceive(mockContext, Intent(Intent.ACTION_BOOT_COMPLETED))
    assertNull(shadowAlarmManager.nextScheduledAlarm)
  }
}
