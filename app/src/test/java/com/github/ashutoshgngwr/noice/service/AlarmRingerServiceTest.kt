package com.github.ashutoshgngwr.noice.service

import android.app.NotificationManager
import android.content.Context
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.TestDispatcherRule
import com.github.ashutoshgngwr.noice.activity.AlarmRingerActivity
import com.github.ashutoshgngwr.noice.di.AlarmRepositoryModule
import com.github.ashutoshgngwr.noice.di.AlarmRingerModule
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import kotlin.time.Duration.Companion.minutes

@HiltAndroidTest
@UninstallModules(AlarmRepositoryModule::class, AlarmRingerModule::class)
@RunWith(RobolectricTestRunner::class)
class AlarmRingerServiceTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @get:Rule
  val testDispatcherRule = TestDispatcherRule()

  @BindValue
  internal lateinit var alarmRepositoryMock: AlarmRepository

  @BindValue
  internal lateinit var presetRepositoryMock: PresetRepository

  @BindValue
  internal lateinit var settingsRepositoryMock: SettingsRepository

  @BindValue
  internal lateinit var playbackServiceControllerMock: SoundPlaybackService.Controller

  @BindValue
  internal lateinit var uiControllerMock: AlarmRingerService.UiController

  @BindValue
  internal lateinit var serviceControllerMock: AlarmRingerActivity.ServiceController

  private lateinit var vibrator: Vibrator
  private lateinit var shadowNotificationManager: ShadowNotificationManager

  @Before
  fun setUp() {
    alarmRepositoryMock = mockk(relaxed = true)
    presetRepositoryMock = mockk(relaxed = true)
    settingsRepositoryMock = mockk {
      every { getAlarmRingerMaxDuration() } returns 2.minutes
      every { getAlarmVolumeRampDuration() } returns 1.minutes
    }

    playbackServiceControllerMock = mockk(relaxed = true)
    uiControllerMock = mockk(relaxed = true)
    serviceControllerMock = mockk(relaxed = true)

    val context = ApplicationProvider.getApplicationContext<Context>()
    vibrator = requireNotNull(context.getSystemService<VibratorManager>()?.defaultVibrator)
    shadowNotificationManager = shadowOf(context.getSystemService<NotificationManager>())
  }

  @Test
  fun ringer() = runTest {
    val presetTypeGenerated = 0
    val presetTypePredefined = 1
    val presetTypeRandom = 2

    data class TestCase(
      val alarm: Alarm,
      val getRandomPreset: Preset?,
      val expectedPresetType: Int,
      val expectVibrate: Boolean,
    )

    val testCases = arrayOf(
      TestCase(
        alarm = buildAlarm(preset = null, false),
        getRandomPreset = null,
        expectedPresetType = presetTypeGenerated,
        expectVibrate = false,
      ),
      TestCase(
        alarm = buildAlarm(preset = null, true),
        getRandomPreset = Preset("preset-2", sortedMapOf()),
        expectedPresetType = presetTypeRandom,
        expectVibrate = true,
      ),
      TestCase(
        alarm = buildAlarm(preset = Preset("test-preset", sortedMapOf()), false),
        getRandomPreset = Preset("preset-1", sortedMapOf()),
        expectedPresetType = presetTypePredefined,
        expectVibrate = false,
      ),
    )

    for (testCase in testCases) {
      clearMocks(alarmRepositoryMock, presetRepositoryMock, playbackServiceControllerMock)
      vibrator.cancel() // ShadowVibrator doesn't have an API to reset its state.
      advanceUntilIdle()

      val generatedPreset = Preset("preset-3", sortedMapOf())
      every {
        presetRepositoryMock.generate(any(), any())
      } returns flowOf(Resource.Success(generatedPreset))

      coEvery { presetRepositoryMock.getRandom() } returns testCase.getRandomPreset
      coEvery { alarmRepositoryMock.get(testCase.alarm.id) } returns testCase.alarm

      val context = ApplicationProvider.getApplicationContext<Context>()
      val serviceController = Robolectric.buildService(
        AlarmRingerService::class.java,
        AlarmRingerService.buildRingIntent(context, testCase.alarm.id)
      ).create()

      serviceController.startCommand(0, 0)
      verify(exactly = 1) {
        playbackServiceControllerMock.setAudioUsage(SoundPlaybackService.Controller.AUDIO_USAGE_ALARM)
        playbackServiceControllerMock.playPreset(withArg { preset: Preset ->
          when (testCase.expectedPresetType) {
            presetTypeGenerated -> assertEquals(generatedPreset, preset)
            presetTypeRandom -> assertEquals(testCase.getRandomPreset, preset)
            presetTypePredefined -> assertEquals(preset, testCase.alarm.preset)
            else -> throw UnsupportedOperationException()
          }
        })
      }

      assertEquals(testCase.expectVibrate, shadowOf(vibrator).isVibrating)
      val notifications = shadowNotificationManager.activeNotifications
        .filter { it.id == AlarmRingerService.NOTIFICATION_ID_ALARM }
      assertEquals(1, notifications.size)
      assertTrue(notifications.firstOrNull()?.isOngoing ?: false)

      // assert master volume fade
      advanceUntilIdle()
      verify(atLeast = 20) { playbackServiceControllerMock.setVolume(any()) }
    }
  }

  @Test
  fun ringerAutoDismiss() = runTest {
    coEvery { alarmRepositoryMock.get(1) } returns buildAlarm(null, true)
    every { settingsRepositoryMock.getAlarmRingerMaxDuration() } returns 2.minutes

    val context = ApplicationProvider.getApplicationContext<Context>()
    val serviceController = Robolectric.buildService(
      AlarmRingerService::class.java,
      AlarmRingerService.buildRingIntent(context, 1)
    ).create()

    serviceController.startCommand(0, 0)

    val advanceTimeBy = arrayOf(0.minutes, 1.minutes, 2.minutes)
    val expectDismissed = arrayOf(false, false, true)
    for (i in advanceTimeBy.indices) {
      this.advanceTimeBy(advanceTimeBy[i].inWholeMilliseconds)
      verify(exactly = if (expectDismissed[i]) 1 else 0) {
        playbackServiceControllerMock.pause(any())
        playbackServiceControllerMock.setAudioUsage(SoundPlaybackService.Controller.AUDIO_USAGE_MEDIA)
        uiControllerMock.dismiss()
      }

      shadowNotificationManager.activeNotifications
        .filter {
          (expectDismissed[i] && it.id == AlarmRingerService.NOTIFICATION_ID_MISSED)
            || it.id == AlarmRingerService.NOTIFICATION_ID_ALARM
        }
        .also { assertTrue(it.isNotEmpty()) }
    }
  }

  @Test
  fun snooze() {
    coEvery { alarmRepositoryMock.get(1) } returns buildAlarm(null, true)
    val context = ApplicationProvider.getApplicationContext<Context>()
    val serviceController = Robolectric.buildService(
      AlarmRingerService::class.java,
      AlarmRingerService.buildSnoozeIntent(context, 1)
    ).create()

    serviceController.startCommand(0, 0)
    coVerify(exactly = 1, timeout = 5000) {
      playbackServiceControllerMock.pause(any())
      playbackServiceControllerMock.setAudioUsage(SoundPlaybackService.Controller.AUDIO_USAGE_MEDIA)
      uiControllerMock.dismiss()
      alarmRepositoryMock.reportTrigger(1, true)
    }

    shadowNotificationManager.activeNotifications
      .filter { it.id == AlarmRingerService.NOTIFICATION_ID_ALARM }
      .also { assertTrue(it.isEmpty()) }
  }

  @Test
  fun dismiss() {
    coEvery { alarmRepositoryMock.get(1) } returns buildAlarm(null, true)
    val context = ApplicationProvider.getApplicationContext<Context>()
    val serviceController = Robolectric.buildService(
      AlarmRingerService::class.java,
      AlarmRingerService.buildDismissIntent(context, 1)
    ).create()

    serviceController.startCommand(0, 0)
    coVerify(exactly = 1, timeout = 5000) {
      playbackServiceControllerMock.pause(any())
      playbackServiceControllerMock.setAudioUsage(SoundPlaybackService.Controller.AUDIO_USAGE_MEDIA)
      uiControllerMock.dismiss()
      alarmRepositoryMock.reportTrigger(1, false)
    }

    shadowNotificationManager.activeNotifications
      .filter { it.id == AlarmRingerService.NOTIFICATION_ID_ALARM }
      .also { assertTrue(it.isEmpty()) }
  }

  private fun buildAlarm(preset: Preset?, vibrate: Boolean): Alarm {
    return Alarm(
      id = 1,
      label = "test-label",
      isEnabled = true,
      minuteOfDay = 0,
      weeklySchedule = 0,
      preset = preset,
      vibrate = vibrate,
    )
  }
}
