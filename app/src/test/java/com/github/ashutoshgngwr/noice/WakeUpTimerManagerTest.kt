package com.github.ashutoshgngwr.noice

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.google.gson.GsonBuilder
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
  private lateinit var mockPresetRepository: PresetRepository
  private lateinit var shadowAlarmManager: ShadowAlarmManager

  @Before
  fun setup() {
    mockkStatic(PreferenceManager::class)
    mockPrefs = mockk(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

    mockkObject(PresetRepository.Companion)
    mockPresetRepository = mockk(relaxed = true)
    every { PresetRepository.newInstance(any()) } returns mockPresetRepository

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

    for (shouldUpdateMediaVolume in arrayOf(true, false)) {
      ShadowPendingIntent.reset()
      val expectedTimer = WakeUpTimerManager.Timer(
        expectedPresetID, expectedTime, shouldUpdateMediaVolume, expectedVolume
      )

      val expectedJSON = GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create()
        .toJson(expectedTimer)

      val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
        every { putString(any(), any()) } returns this
      }

      every { mockPrefs.edit() } returns mockPrefsEditor
      every { mockPresetRepository.get(expectedPresetID) } returns mockk()

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

    every { mockPresetRepository.get("test") } returns mockk()

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
    val context = ApplicationProvider.getApplicationContext<Context>()
    every { mockPrefs.getString(WakeUpTimerManager.PREF_LAST_USED_PRESET_ID, any()) } returns null
    every { mockPresetRepository.get(null) } returns null
    assertNull(WakeUpTimerManager.getLastUsedPresetID(context))

    val presetID = "test-preset-id"
    every {
      mockPrefs.getString(WakeUpTimerManager.PREF_LAST_USED_PRESET_ID, any())
    } returns presetID

    // preset doesn't exist
    every { mockPresetRepository.get(presetID) } returns null
    assertNull(WakeUpTimerManager.getLastUsedPresetID(context))

    // preset doesn't exist
    every { mockPresetRepository.get(presetID) } returns mockk {
      every { id } returns presetID
    }

    assertEquals(presetID, WakeUpTimerManager.getLastUsedPresetID(context))
  }
}
