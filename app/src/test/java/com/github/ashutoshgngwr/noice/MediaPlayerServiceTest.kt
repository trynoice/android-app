package com.github.ashutoshgngwr.noice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import io.mockk.MockKAnnotations
import io.mockk.called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifySequence
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class MediaPlayerServiceTest {

  @RelaxedMockK
  private lateinit var playerManager: PlayerManager

  @RelaxedMockK
  private lateinit var presetRepository: PresetRepository

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var service: MediaPlayerService

  private lateinit var serviceController: ServiceController<MediaPlayerService>
  private lateinit var mockEventBus: EventBus
  private lateinit var mockServiceIntent: Intent

  @Before
  fun setup() {
    mockkStatic(EventBus::class)
    mockEventBus = mockk(relaxed = true)
    every { EventBus.getDefault() } returns mockEventBus

    mockServiceIntent = mockk(relaxed = true)
    serviceController = Robolectric.buildService(MediaPlayerService::class.java, mockServiceIntent)
    service = serviceController.get()
    MockKAnnotations.init(this)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testOnPlayerUpdateListener_onOngoingPlayback() {
    val listenerSlot = slot<() -> Unit>()
    every { playerManager.setOnPlayerUpdateListener(capture(listenerSlot)) } answers { }

    serviceController.create() // will register the listener
    val wakeLock = ShadowPowerManager.getLatestWakeLock()
    assertFalse(wakeLock.isHeld) // should not be held by default

    val players = hashMapOf<String, Player>()
    every { playerManager.state } returns PlayerManager.State.PLAYING
    every { playerManager.players } returns players
    listenerSlot.invoke() // invoke the listener

    assertTrue(wakeLock.isHeld)
    val eventSlot = slot<MediaPlayerService.OnPlayerManagerUpdateEvent>()
    verify(exactly = 1) { mockEventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlayerManager.State.PLAYING, eventSlot.captured.state)
    assertEquals(players, eventSlot.captured.players)

    clearMocks(mockEventBus)
    every { playerManager.state } returns PlayerManager.State.PAUSED
    listenerSlot.invoke()
    assertTrue(wakeLock.isHeld) // should not release the wakelock
    verify(exactly = 1) { mockEventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlayerManager.State.PAUSED, eventSlot.captured.state)

    clearMocks(mockEventBus)
    every { playerManager.state } returns PlayerManager.State.STOPPED
    listenerSlot.invoke()
    assertFalse(wakeLock.isHeld) // should release the wakelock
    verify(exactly = 1) { mockEventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlayerManager.State.STOPPED, eventSlot.captured.state)
  }

  @Test
  fun testWakeLock_onServiceDestroy() {
    serviceController.create().destroy()
    assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
  }

  @Test
  fun testOnStartCommand_withoutAction() {
    serviceController.startCommand(0, 0)
    verify { playerManager wasNot called }
  }


  @Test
  fun testOnStartCommand_withResumePlaybackAction() {
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_RESUME_PLAYBACK
    serviceController.startCommand(0, 0)
    verify(exactly = 1) { playerManager.resume() }
  }

  @Test
  fun testOnStartCommand_withPausePlaybackAction() {
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_PAUSE_PLAYBACK
    serviceController.startCommand(0, 0)
    verify(exactly = 1) { playerManager.pause() }
  }

  @Test
  fun testOnStartCommand_withStopPlaybackAction() {
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_STOP_PLAYBACK
    serviceController.startCommand(0, 0)
    verify(exactly = 1) { playerManager.stop() }
  }

  @Test
  fun testOnStartCommand_withPlayPresetAction() {
    every { presetRepository.get("test") } returns mockk(relaxed = true)
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_PLAY_PRESET
    every { mockServiceIntent.getStringExtra(MediaPlayerService.EXTRA_PRESET_ID) } returns "test"

    val volume = 10
    every {
      mockServiceIntent.getIntExtra(MediaPlayerService.EXTRA_DEVICE_MEDIA_VOLUME, any())
    } returns volume

    serviceController.startCommand(0, 0)
    verifySequence { playerManager.playPreset(any()) }

    assertEquals(
      volume,
      ApplicationProvider.getApplicationContext<Context>()
        .getSystemService<AudioManager>()
        ?.getStreamVolume(AudioManager.STREAM_MUSIC)
    )
  }

  @Test
  fun testOnStartCommand_withPlaySoundAction() {
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_PLAY_SOUND
    every { mockServiceIntent.getStringExtra(MediaPlayerService.EXTRA_SOUND_KEY) } returns "test"
    serviceController.startCommand(0, 0)
    verify(exactly = 1) { playerManager.play("test") }
  }

  @Test
  fun testOnStartCommand_withStopSoundAction() {
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_STOP_SOUND
    every { mockServiceIntent.getStringExtra(MediaPlayerService.EXTRA_SOUND_KEY) } returns "test"
    serviceController.startCommand(0, 0)
    verify(exactly = 1) { playerManager.stop("test") }
  }

  @Test
  fun testOnStartCommand_withScheduleStopPlaybackAction_onSchedule() {
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_SCHEDULE_STOP_PLAYBACK
    every {
      mockServiceIntent.getLongExtra(MediaPlayerService.EXTRA_AT_UPTIME_MILLIS, any())
    } returns SystemClock.uptimeMillis() + 100

    serviceController.startCommand(0, 0)
    verify { playerManager wasNot called }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify(exactly = 1) { playerManager.pause() }
  }

  @Test
  fun testOnStartCommand_withScheduleStopPlaybackAction_onCancel() {
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_SCHEDULE_STOP_PLAYBACK
    every {
      mockServiceIntent.getLongExtra(MediaPlayerService.EXTRA_AT_UPTIME_MILLIS, any())
    } returns SystemClock.uptimeMillis() + 100

    serviceController.startCommand(0, 0)

    // cancel auto stop.
    every {
      mockServiceIntent.getLongExtra(MediaPlayerService.EXTRA_AT_UPTIME_MILLIS, any())
    } returns 0

    serviceController.startCommand(0, 0)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify { playerManager wasNot called }
  }

  @Test
  fun testPlaySound() {
    val mockContext = mockk<Context>(relaxed = true)
    MediaPlayerService.playSound(mockContext, "test")
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService.ACTION_PLAY_SOUND, it.action)
          assertEquals("test", it.getStringExtra(MediaPlayerService.EXTRA_SOUND_KEY))
        }
      )
    }
  }

  @Test
  fun testStopSound() {
    val mockContext = mockk<Context>(relaxed = true)
    MediaPlayerService.stopSound(mockContext, "test")
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService.ACTION_STOP_SOUND, it.action)
          assertEquals("test", it.getStringExtra(MediaPlayerService.EXTRA_SOUND_KEY))
        }
      )
    }
  }

  @Test
  fun testResumePlayback() {
    val mockContext = mockk<Context>(relaxed = true)
    MediaPlayerService.resumePlayback(mockContext)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService.ACTION_RESUME_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testPausePlayback() {
    val mockContext = mockk<Context>(relaxed = true)
    MediaPlayerService.pausePlayback(mockContext)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService.ACTION_PAUSE_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testStopPlayback() {
    val mockContext = mockk<Context>(relaxed = true)
    MediaPlayerService.stopPlayback(mockContext)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService.ACTION_STOP_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testPlayPreset() {
    val mockContext = mockk<Context>(relaxed = true)
    MediaPlayerService.playPreset(mockContext, "test")
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService.ACTION_PLAY_PRESET, it.action)
          assertEquals("test", it.getStringExtra(MediaPlayerService.EXTRA_PRESET_ID))
        }
      )
    }
  }

  @Test
  fun testPlayRandomPreset() {
    val mockContext = mockk<Context>(relaxed = true)
    val tag = mockk<Sound.Tag>(relaxed = true)
    val intensity = 1 until 10

    MediaPlayerService.playRandomPreset(mockContext, tag, intensity)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService.ACTION_PLAY_RANDOM_PRESET, it.action)
          assertEquals(
            intensity.first,
            it.getIntExtra(MediaPlayerService.EXTRA_RANDOM_PRESET_MIN_SOUNDS, 0)
          )

          assertEquals(
            intensity.last,
            it.getIntExtra(MediaPlayerService.EXTRA_RANDOM_PRESET_MAX_SOUNDS, 0)
          )

          assertEquals(
            tag,
            it.getSerializableExtra(MediaPlayerService.EXTRA_FILTER_SOUNDS_BY_TAG) as Sound.Tag
          )
        }
      )
    }
  }

  @Test
  fun testScheduleStopPlayback() {
    val mockContext = mockk<Context>(relaxed = true)
    val duration = TimeUnit.SECONDS.toMillis(1)
    val before = SystemClock.uptimeMillis() + duration
    MediaPlayerService.scheduleStopPlayback(mockContext, duration)
    val after = SystemClock.uptimeMillis() + duration
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService.ACTION_SCHEDULE_STOP_PLAYBACK, it.action)
          assertTrue(
            it.getLongExtra(MediaPlayerService.EXTRA_AT_UPTIME_MILLIS, 0) in before..after
          )
        }
      )
    }
  }
}
