package com.github.ashutoshgngwr.noice.playback

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import androidx.media.AudioAttributesCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.model.Sound
import io.mockk.called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class PlaybackControllerTest {

  private lateinit var mockContext: Context
  private lateinit var mockPrefs: SharedPreferences
  private lateinit var mockPlayerManager: PlayerManager
  private lateinit var playbackController: PlaybackController

  @Before
  fun setup() {
    mockContext = mockk(relaxed = true)
    mockPrefs = mockk(relaxed = true)
    mockPlayerManager = mockk(relaxed = true)
    mockkStatic(PreferenceManager::class)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

    playbackController = PlaybackController(mockContext, mockk(relaxed = true))
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testBuildResumeActionPendingIntent() {
    val pendingIntent = PlaybackController.buildResumeActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_RESUME_PLAYBACK, intent.action)
  }

  @Test
  fun testBuildPauseActionPendingIntent() {
    val pendingIntent = PlaybackController.buildPauseActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_PAUSE_PLAYBACK, intent.action)
  }

  @Test
  fun testBuildStopActionPendingIntent() {
    val pendingIntent = PlaybackController.buildStopActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_STOP_PLAYBACK, intent.action)
  }

  @Test
  fun testBuildSkipPrevActionPendingIntent() {
    val pendingIntent = PlaybackController.buildSkipPrevActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_SKIP_PRESET, intent.action)
    assertEquals(
      PlayerManager.SKIP_DIRECTION_PREV,
      intent.getIntExtra(PlaybackController.EXTRA_SKIP_DIRECTION, 0)
    )
  }

  @Test
  fun testBuildSkipNextActionPendingIntent() {
    val pendingIntent = PlaybackController.buildSkipNextActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_SKIP_PRESET, intent.action)
    assertEquals(
      PlayerManager.SKIP_DIRECTION_NEXT,
      intent.getIntExtra(PlaybackController.EXTRA_SKIP_DIRECTION, 0)
    )
  }

  @Test
  fun testHandleServiceIntent_withResumePlaybackAction() {
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_RESUME_PLAYBACK)
    )

    verify(exactly = 1) { mockPlayerManager.resume() }
  }

  @Test
  fun testHandleServiceIntent_withPausePlaybackAction() {
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PAUSE_PLAYBACK),
    )

    verify(exactly = 1) { mockPlayerManager.pause() }
  }

  @Test
  fun testHandleServiceIntent_withStopPlaybackAction() {
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_STOP_PLAYBACK),
    )

    verify(exactly = 1) { mockPlayerManager.stop() }
  }

  @Test
  fun testHandleServiceIntent_withPlayPresetAction() {
    val presetID = "test-id"

    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PLAY_PRESET)
        .putExtra(PlaybackController.EXTRA_PRESET_ID, presetID),
    )

    val uri = Uri.parse("test")
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PLAY_PRESET)
        .setData(uri),
    )

    verifyOrder {
      mockPlayerManager.playPreset(presetID)
      mockPlayerManager.playPreset(uri)
    }
  }

  @Test
  fun testHandleServiceIntent_withPlayRandomPresetAction() {
    val tag = mockk<Sound.Tag>(relaxed = true)
    val minSounds = Random.nextInt()
    val maxSounds = Random.nextInt(minSounds, minSounds + 10)
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PLAY_RANDOM_PRESET)
        .putExtra(PlaybackController.EXTRA_FILTER_SOUNDS_BY_TAG, tag)
        .putExtra(PlaybackController.EXTRA_RANDOM_PRESET_MIN_SOUNDS, minSounds)
        .putExtra(PlaybackController.EXTRA_RANDOM_PRESET_MAX_SOUNDS, maxSounds),
    )

    verify(exactly = 1) { mockPlayerManager.playRandomPreset(tag, minSounds..maxSounds) }
  }

  @Test
  fun testHandleServiceIntent_withPlaySoundAction() {
    val soundKey = "test-sound-key"
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PLAY_SOUND)
        .putExtra(PlaybackController.EXTRA_SOUND_KEY, soundKey),
    )

    verify(exactly = 1) { mockPlayerManager.play(soundKey) }
  }

  @Test
  fun testHandleServiceIntent_withStopSoundAction() {
    val soundKey = "test-sound-key"
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_STOP_SOUND)
        .putExtra(PlaybackController.EXTRA_SOUND_KEY, soundKey),
    )

    verify(exactly = 1) { mockPlayerManager.stop(soundKey) }
  }

  @Test
  fun testHandleServiceIntent_withScheduleStopPlaybackAction_onSchedule() {
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, SystemClock.uptimeMillis() + 100),
    )

    verify { mockPlayerManager wasNot called }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify(exactly = 1) { mockPlayerManager.pause() }
  }

  @Test
  fun testHandleServiceIntent_withScheduleStopPlaybackAction_onCancel() {
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, SystemClock.uptimeMillis() + 1000),
    )

    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, SystemClock.uptimeMillis() - 1000),
    )

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify { mockPlayerManager wasNot called }
  }

  @Test
  fun testHandleServiceIntent_withSkipPresetAction() {
    for (input in arrayOf(PlayerManager.SKIP_DIRECTION_PREV, PlayerManager.SKIP_DIRECTION_NEXT)) {
      playbackController.handleServiceIntent(
        mockPlayerManager,
        Intent(PlaybackController.ACTION_SKIP_PRESET)
          .putExtra(PlaybackController.EXTRA_SKIP_DIRECTION, input),
      )

      verify(exactly = 1) { mockPlayerManager.skipPreset(input) }
      clearMocks(mockPlayerManager)
    }
  }

  fun testHandleServiceIntent_withRequestUpdateEventAction() {
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_REQUEST_UPDATE_EVENT),
    )

    verify(exactly = 1) { mockPlayerManager.callPlaybackUpdateListener() }
  }

  fun testHandleServiceIntent_withSetAudioUsageAction() {
    val audioUsage = AudioAttributesCompat.USAGE_ALARM
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SET_AUDIO_USAGE)
        .putExtra(PlaybackController.EXTRA_AUDIO_USAGE, audioUsage),
    )

    verify(exactly = 1) { mockPlayerManager.setAudioUsage(audioUsage) }
  }

  @Test
  fun testHandleServiceIntent_withoutAction() {
    playbackController.handleServiceIntent(mockPlayerManager, Intent())
    verify { mockPlayerManager wasNot called }
  }

  @Test
  fun testPlaySound() {
    val soundKey = "test-sound-key"
    playbackController.play(soundKey)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PLAY_SOUND, it.action)
          assertEquals(soundKey, it.getStringExtra(PlaybackController.EXTRA_SOUND_KEY))
        }
      )
    }
  }

  @Test
  fun testStopSound() {
    val soundKey = "test-sound-key"
    playbackController.stop(soundKey)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_STOP_SOUND, it.action)
          assertEquals(soundKey, it.getStringExtra(PlaybackController.EXTRA_SOUND_KEY))
        }
      )
    }
  }

  @Test
  fun testResumePlayback() {
    playbackController.resume()
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_RESUME_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testPausePlayback() {
    playbackController.pause()
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PAUSE_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testStopPlayback() {
    playbackController.stop()
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_STOP_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testPlayPreset() {
    playbackController.playPreset("test")
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PLAY_PRESET, it.action)
          assertEquals("test", it.getStringExtra(PlaybackController.EXTRA_PRESET_ID))
        }
      )
    }
  }

  @Test
  fun testPlayPresetFromUri() {
    val uri = Uri.parse("test")
    playbackController.playPresetFromUri(uri)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PLAY_PRESET, it.action)
          assertEquals(uri, it.data)
        }
      )
    }
  }

  @Test
  fun testPlayRandomPreset() {
    val tag = mockk<Sound.Tag>(relaxed = true)
    val intensity = 1 until 10

    playbackController.playRandomPreset(tag, intensity)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PLAY_RANDOM_PRESET, it.action)
          assertEquals(
            intensity.first,
            it.getIntExtra(PlaybackController.EXTRA_RANDOM_PRESET_MIN_SOUNDS, 0)
          )

          assertEquals(
            intensity.last,
            it.getIntExtra(PlaybackController.EXTRA_RANDOM_PRESET_MAX_SOUNDS, 0)
          )

          assertEquals(
            tag,
            it.getSerializableExtra(PlaybackController.EXTRA_FILTER_SOUNDS_BY_TAG) as Sound.Tag
          )
        }
      )
    }
  }

  @Test
  fun testScheduleAutoStop() {
    val mockPrefsEditor = mockk<SharedPreferences.Editor> {
      every { putLong(any(), any()) } returns this
      every { commit() } returns true
    }

    every { mockPrefs.edit() } returns mockPrefsEditor

    val duration = TimeUnit.SECONDS.toMillis(1)
    val before = SystemClock.uptimeMillis() + duration
    playbackController.scheduleAutoStop(duration)
    val after = SystemClock.uptimeMillis() + duration
    verify(exactly = 1) {
      mockPrefsEditor.putLong(PlaybackController.PREF_LAST_SCHEDULED_STOP_TIME, withArg {
        assertTrue(it in before..after)
      })

      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK, it.action)
          assertTrue(it.getLongExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, 0) in before..after)
        }
      )
    }
  }

  @Test
  fun testClearScheduledAutoStop() {
    playbackController.clearScheduledAutoStop()
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK, it.action)

          val atUptimeMillis = it.getLongExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, 0)
          assertTrue(atUptimeMillis < SystemClock.uptimeMillis())
        }
      )
    }
  }

  @Test
  fun testGetScheduledAutoStopRemainingDurationMillis() {
    every {
      mockPrefs.getLong(PlaybackController.PREF_LAST_SCHEDULED_STOP_TIME, any())
    } returns SystemClock.uptimeMillis() + 1000L

    val r = playbackController.getScheduledAutoStopRemainingDurationMillis()
    assertTrue(r in 900..1000)
  }

  @Test
  fun testClearAutoStopCallback() {
    playbackController.handleServiceIntent(
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, SystemClock.uptimeMillis() + 1000),
    )

    playbackController.clearAutoStopCallback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify { mockPlayerManager wasNot called }
  }

  @Test
  fun testRequestUpdateEvent() {
    playbackController.requestUpdateEvent()
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_REQUEST_UPDATE_EVENT, it.action)
        }
      )
    }
  }

  @Test
  fun testSetAudioUsage() {
    val audioUsage = AudioAttributesCompat.USAGE_ALARM
    playbackController.setAudioUsage(audioUsage)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_SET_AUDIO_USAGE, it.action)
          assertEquals(audioUsage, it.getIntExtra(PlaybackController.EXTRA_AUDIO_USAGE, -1))
        }
      )
    }
  }
}
