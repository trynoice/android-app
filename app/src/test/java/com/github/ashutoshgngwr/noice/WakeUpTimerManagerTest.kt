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
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

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

  @Test
  fun testSet() {
    val expectedTriggerTime = System.currentTimeMillis() + 10000L
    val expectedTimer = WakeUpTimerManager.Timer("test", expectedTriggerTime)
    val expectedJSON = withGson { it.toJson(expectedTimer) }
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putString(any(), any()) } returns this
    }

    every { mockPrefs.edit() } returns mockPrefsEditor
    mockkObject(Preset.Companion)
    every { Preset.findByID(any(), "test") } returns mockk()

    WakeUpTimerManager.set(ApplicationProvider.getApplicationContext(), expectedTimer)

    verifyOrder {
      mockPrefs.edit()
      mockPrefsEditor.putString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, expectedJSON)
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
    } returns """{"presetID": "test", "atMillis": 1}"""

    val timer = WakeUpTimerManager.get(ApplicationProvider.getApplicationContext())
    assertEquals(1L, timer?.atMillis)
    assertEquals("test", timer?.presetID)
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
}
