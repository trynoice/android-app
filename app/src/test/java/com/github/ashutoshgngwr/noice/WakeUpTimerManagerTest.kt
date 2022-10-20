package com.github.ashutoshgngwr.noice

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class WakeUpTimerManagerTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var mockPrefs: SharedPreferences
  private lateinit var shadowAlarmManager: ShadowAlarmManager
  private lateinit var mockPresetRepository: PresetRepository
  private lateinit var wakeUpTimerManager: WakeUpTimerManager

  @set:Inject
  internal lateinit var gson: Gson

  @Before
  fun setup() {
    hiltRule.inject()
    mockkStatic(PreferenceManager::class)
    mockPrefs = mockk(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

    mockPresetRepository = mockk(relaxed = true)
    val context = ApplicationProvider.getApplicationContext<Context>()
    shadowAlarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))
    wakeUpTimerManager = WakeUpTimerManager(context, mockPresetRepository, gson)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testSet() {
    // TODO:
//    val expectedTime = System.currentTimeMillis() + 10000L
//    val expectedPresetID = "test-preset-id"
//    val expectedTimer = WakeUpTimerManager.Timer(expectedPresetID, expectedTime)
//    val expectedJSON = GsonBuilder()
//      .excludeFieldsWithoutExposeAnnotation()
//      .create()
//      .toJson(expectedTimer)
//
//    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
//      every { putString(any(), any()) } returns this
//    }
//
//    every { mockPrefs.edit() } returns mockPrefsEditor
//    every { mockPresetRepository.get(expectedPresetID) } returns mockk()
//
//    wakeUpTimerManager.set(expectedTimer)
//
//    verifyOrder {
//      mockPrefs.edit()
//      mockPrefsEditor.putString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, expectedJSON)
//      mockPrefsEditor.apply()
//    }
//
//    val alarm = shadowAlarmManager.nextScheduledAlarm
//    val i = shadowOf(alarm.operation).savedIntent
//    assertEquals(expectedTime, alarm.triggerAtTime)
//    assertEquals(expectedPresetID, i.getStringExtra(PlaybackSer))
  }

  @Test
  fun testCancel() {
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { remove(any()) } returns this
    }

    every { mockPrefs.edit() } returns mockPrefsEditor
    wakeUpTimerManager.cancel()

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
    assertNull(wakeUpTimerManager.get())

    every {
      mockPrefs.getString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, any())
    } returns """{"presetID": "test", "atMillis": 1, "mediaVolume": 10}"""

    val timer = wakeUpTimerManager.get()
    assertEquals(1L, timer?.atMillis)
    assertEquals("test", timer?.presetID)
  }

  @Test
  fun rescheduleExistingTimer_whenTimerIsPreScheduled() {
    val expectedTime = System.currentTimeMillis() + 1000L
    every {
      mockPrefs.getString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, any())
    } returns """{"presetID": "test", "atMillis": $expectedTime}"""

    every { mockPresetRepository.get("test") } returns mockk()

    wakeUpTimerManager.rescheduleExistingTimer()
    assertEquals(expectedTime, shadowAlarmManager.nextScheduledAlarm.triggerAtTime)
  }

  @Test
  fun rescheduleExistingTimer_whenTimeIsNotPreScheduled() {
    every { mockPrefs.getString(WakeUpTimerManager.PREF_WAKE_UP_TIMER, any()) } returns null
    wakeUpTimerManager.rescheduleExistingTimer()
    assertNull(shadowAlarmManager.nextScheduledAlarm)
  }

  @Test
  fun testSaveLastUsedPresetID() {
    val presetID = "test-preset-id"
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putString(any(), any()) } returns this
    }

    every { mockPrefs.edit() } returns mockPrefsEditor
    wakeUpTimerManager.saveLastUsedPresetID(presetID)
    verify(exactly = 1) {
      mockPrefs.edit()
      mockPrefsEditor.putString(WakeUpTimerManager.PREF_LAST_USED_PRESET_ID, presetID)
      mockPrefsEditor.apply()
    }
  }

  @Test
  fun testGetLastUsedPresetID() {
    every { mockPrefs.getString(WakeUpTimerManager.PREF_LAST_USED_PRESET_ID, any()) } returns null
    every { mockPresetRepository.get(null) } returns null
    assertNull(wakeUpTimerManager.getLastUsedPresetID())

    val presetID = "test-preset-id"
    every {
      mockPrefs.getString(WakeUpTimerManager.PREF_LAST_USED_PRESET_ID, any())
    } returns presetID

    // preset doesn't exist
    every { mockPresetRepository.get(presetID) } returns null
    assertNull(wakeUpTimerManager.getLastUsedPresetID())

    // preset doesn't exist
    every { mockPresetRepository.get(presetID) } returns mockk {
      every { id } returns presetID
    }

    assertEquals(presetID, wakeUpTimerManager.getLastUsedPresetID())
  }
}
