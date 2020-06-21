package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.os.SystemClock
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import io.mockk.*
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.impl.annotations.RelaxedMockK
import org.greenrobot.eventbus.EventBus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
class MediaPlayerServiceTest {

  @RelaxedMockK
  private lateinit var eventBus: EventBus

  @RelaxedMockK
  private lateinit var playerManager: PlayerManager

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var service: MediaPlayerService

  private lateinit var serviceController: ServiceController<MediaPlayerService>
  private lateinit var mockServiceIntent: Intent

  @Before
  fun setup() {
    mockServiceIntent = mockk(relaxed = true)
    serviceController = Robolectric.buildService(MediaPlayerService::class.java, mockServiceIntent)
    service = serviceController.get()
    MockKAnnotations.init(this)
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
    verify(exactly = 1) { eventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlayerManager.State.PLAYING, eventSlot.captured.state)
    assertEquals(players, eventSlot.captured.players)

    clearMocks(eventBus)
    every { playerManager.state } returns PlayerManager.State.PAUSED
    listenerSlot.invoke()
    assertTrue(wakeLock.isHeld) // should not release the wakelock
    verify(exactly = 1) { eventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlayerManager.State.PAUSED, eventSlot.captured.state)

    clearMocks(eventBus)
    every { playerManager.state } returns PlayerManager.State.STOPPED
    listenerSlot.invoke()
    assertFalse(wakeLock.isHeld) // should release the wakelock
    verify(exactly = 1) { eventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlayerManager.State.STOPPED, eventSlot.captured.state)
  }

  @Test
  fun testWakeLock_onServiceDestroy() {
    serviceController.create().destroy()
    assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
  }

  @Test
  fun testOnStartCommand() {
    // without any command
    serviceController.startCommand(0, 0)
    verify { playerManager wasNot called }

    // send resume command
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_START_PLAYBACK
    serviceController.startCommand(0, 0)

    // send pause command
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_PAUSE_PLAYBACK
    serviceController.startCommand(0, 0)

    // send stop command
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_STOP_PLAYBACK
    serviceController.startCommand(0, 0)

    // send play preset command
    mockkObject(Preset.Companion)
    every { Preset.findByName(any(), "test") } returns mockk(relaxed = true)
    every { mockServiceIntent.action } returns MediaPlayerService.ACTION_PLAY_PRESET
    every { mockServiceIntent.getStringExtra(MediaPlayerService.EXTRA_PRESET_NAME) } returns "test"
    serviceController.startCommand(0, 0)

    verifySequence {
      playerManager.resume()
      playerManager.pause()
      playerManager.stop()
      playerManager.playPreset(any())
    }
  }

  @Test
  fun testPlaybackEventSubscribers() {
    mockkObject(Sound.Companion)

    // start player event
    service.startPlayer(MediaPlayerService.StartPlayerEvent("test"))
    verify(exactly = 1) { playerManager.play("test") }

    clearMocks(playerManager)

    // stop player event
    service.stopPlayer(MediaPlayerService.StopPlayerEvent("test"))
    verify(exactly = 1) { playerManager.stop("test") }
    clearMocks(playerManager)

    // resume playback event
    service.resumePlayback(MediaPlayerService.ResumePlaybackEvent())
    verify(exactly = 1) { playerManager.resume() }
    clearMocks(playerManager)

    // pause playback event
    service.pausePlayback(MediaPlayerService.PausePlaybackEvent())
    verify(exactly = 1) { playerManager.pause() }
    clearMocks(playerManager)

    // stop playback event
    service.stopPlayback(MediaPlayerService.StopPlaybackEvent())
    verify(exactly = 1) { playerManager.stop() }
    clearMocks(playerManager)

    // play preset event
    val preset = mockk<Preset>(relaxed = true)
    service.playPreset(MediaPlayerService.PlayPresetEvent(preset))
    verify(exactly = 1) { playerManager.playPreset(preset) }
  }

  @Test
  fun testScheduleAutoSleepEventSubscriber_onSchedule() {
    service.scheduleAutoStop(MediaPlayerService.ScheduleAutoSleepEvent(SystemClock.uptimeMillis() + 100))
    verify { playerManager wasNot called }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify(exactly = 1) { playerManager.stop() }
  }

  @Test
  fun testScheduleAutoSleepEventSubscriber_onCancel() {
    // schedule auto sleep
    service.scheduleAutoStop(MediaPlayerService.ScheduleAutoSleepEvent(SystemClock.uptimeMillis() + 100))

    // cancel auto sleep
    service.scheduleAutoStop(MediaPlayerService.ScheduleAutoSleepEvent(0))
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    verify { playerManager wasNot called }
  }
}
