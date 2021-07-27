package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.playback.PlaybackUpdateListener
import com.github.ashutoshgngwr.noice.playback.Player
import com.github.ashutoshgngwr.noice.playback.PlayerManager
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
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

@RunWith(RobolectricTestRunner::class)
class MediaPlayerServiceTest {

  private lateinit var serviceController: ServiceController<MediaPlayerService>
  private lateinit var mockEventBus: EventBus
  private lateinit var mockServiceIntent: Intent

  @Before
  fun setup() {
    // mock so that service is created (onCreate calls CastAPIProvider constructor via PlayerManager).
    ApplicationProvider.getApplicationContext<NoiceApplication>()
      .setCastAPIProviderFactory(
        mockk {
          every { newInstance(any()) } returns mockk(relaxed = true)
        }
      )

    mockkStatic(EventBus::class)
    mockEventBus = mockk(relaxed = true)
    every { EventBus.getDefault() } returns mockEventBus

    mockServiceIntent = mockk(relaxed = true)
    serviceController = Robolectric.buildService(MediaPlayerService::class.java, mockServiceIntent)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testOnPlayerUpdateListener_onOngoingPlayback() {
    mockkConstructor(PlayerManager::class)

    val listenerSlot = slot<PlaybackUpdateListener>()
    every {
      anyConstructed<PlayerManager>().setPlaybackUpdateListener(capture(listenerSlot))
    } returns Unit

    serviceController.create() // will register the listener\

    val wakeLock = ShadowPowerManager.getLatestWakeLock()
    assertFalse(wakeLock.isHeld) // should not be held by default

    val players = hashMapOf<String, Player>()
    listenerSlot.invoke(PlaybackStateCompat.STATE_PLAYING, players) // invoke the listener

    assertTrue(wakeLock.isHeld)
    val eventSlot = slot<MediaPlayerService.PlaybackUpdateEvent>()
    verify(exactly = 1) { mockEventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlaybackStateCompat.STATE_PLAYING, eventSlot.captured.state)
    assertEquals(players, eventSlot.captured.players)

    clearMocks(mockEventBus)
    listenerSlot.invoke(PlaybackStateCompat.STATE_PAUSED, players)
    assertTrue(wakeLock.isHeld) // should not release the wakelock
    verify(exactly = 1) { mockEventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlaybackStateCompat.STATE_PAUSED, eventSlot.captured.state)

    clearMocks(mockEventBus)
    listenerSlot.invoke(PlaybackStateCompat.STATE_STOPPED, players)
    assertFalse(wakeLock.isHeld) // should release the wakelock
    verify(exactly = 1) { mockEventBus.postSticky(capture(eventSlot)) }
    assertEquals(PlaybackStateCompat.STATE_STOPPED, eventSlot.captured.state)
  }

  @Test
  fun testWakeLock_onServiceDestroy() {
    mockkObject(PlaybackController)
    serviceController.create().destroy()
    ShadowLooper.idleMainLooper()
    assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    verify(exactly = 1) { PlaybackController.clearAutoStopCallback() }
  }

  @Test
  fun testOnStartCommand() {
    mockkObject(PlaybackController)
    serviceController.create().startCommand(0, 0)
    verify(exactly = 1) {
      PlaybackController.handleServiceIntent(any(), any(), mockServiceIntent)
    }
  }
}
