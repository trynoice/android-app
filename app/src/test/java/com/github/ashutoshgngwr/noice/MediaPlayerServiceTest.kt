package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.playback.PlaybackUpdateListener
import com.github.ashutoshgngwr.noice.playback.Player
import com.github.ashutoshgngwr.noice.playback.PlayerManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager

@HiltAndroidTest
@UninstallModules(NoiceApplication.EventBusModule::class)
@RunWith(RobolectricTestRunner::class)
class MediaPlayerServiceTest {

  private lateinit var serviceController: ServiceController<MediaPlayerService>
  private lateinit var mockServiceIntent: Intent

  @get:Rule
  var hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var mockEventBus: EventBus

  @BindValue
  internal lateinit var mockPlaybackController: PlaybackController

  @Before
  fun setup() {
    mockEventBus = mockk(relaxed = true)
    mockPlaybackController = mockk(relaxed = true)
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
    serviceController.create().destroy()
    ShadowLooper.idleMainLooper()
    assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    verify(exactly = 1) { mockPlaybackController.clearAutoStopCallback() }
  }

  @Test
  fun testOnStartCommand() {
    serviceController.create().startCommand(0, 0)
    verify(exactly = 1) {
      mockPlaybackController.handleServiceIntent(any(), mockServiceIntent)
    }
  }
}
