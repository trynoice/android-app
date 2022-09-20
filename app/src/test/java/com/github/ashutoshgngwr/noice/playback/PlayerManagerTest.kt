package com.github.ashutoshgngwr.noice.playback

import android.content.Context
import android.media.AudioManager
import android.media.VolumeProvider
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioManagerCompat
import androidx.media.VolumeProviderCompat
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.CastApiProviderModule
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategyFactory
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.shadow.ShadowMediaSession
import com.github.ashutoshgngwr.noice.shadow.ShadowMediaSessionCompat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.MockKAnnotations
import io.mockk.called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@HiltAndroidTest
@UninstallModules(CastApiProviderModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMediaSession::class, ShadowMediaSessionCompat::class])
class PlayerManagerTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var players: HashMap<String, Player>
  private lateinit var settingsRepository: SettingsRepository

  @BindValue
  internal lateinit var mockCastApiProvider: CastApiProvider

  @OverrideMockKs
  private lateinit var playerManager: PlayerManager

  @Before
  fun setup() {
    // always have a fake player in manager's state
    players = hashMapOf("test" to mockk(relaxed = true) {
      every { soundKey } returns "test"
    })

    settingsRepository = mockk(relaxed = true)
    mockCastApiProvider = mockk(relaxed = true)
    val context = ApplicationProvider.getApplicationContext<Context>()
    playerManager = PlayerManager(context, MediaSessionCompat(context, "test"))
    MockKAnnotations.init(this)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  private fun assertPaused() {
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assertEquals(PlaybackStateCompat.STATE_STOPPED, ShadowMediaSessionCompat.getLastPlaybackState())
    assertEquals(0, players.size)
  }

  private fun assertPausedIndefinitely() {
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testOnAudioFocusChange() {
    // should pause players on focus loss transient
    playerManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
    verify(exactly = 1) { players.getValue("test").pause() }
    assertPausedIndefinitely()
    clearMocks(players.getValue("test"))

    // should resume players on focus gain
    playerManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    assertEquals(PlaybackStateCompat.STATE_PLAYING, ShadowMediaSessionCompat.getLastPlaybackState())
    verify(exactly = 1) { players.getValue("test").play() }
    clearMocks(players.getValue("test"))

    // should pause players on focus loss
    playerManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
    verify(exactly = 1) { players.getValue("test").pause() }
    assertPaused()
  }

  @Test
  fun testPlaySound_whenIgnoringAudioFocusChanges() {
    mockkStatic(AudioManagerCompat::class)
    every { settingsRepository.shouldIgnoreAudioFocusChanges() } returns true

    playerManager.play("test")
    verify(exactly = 1) { players.getValue("test").play() }
    verify(exactly = 0) { AudioManagerCompat.requestAudioFocus(any(), any()) }
  }

  @Test
  fun testPlaySound_whenAudioFocusRequestFails() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

    playerManager.play("test")
    verify(exactly = 0) { players.getValue("test").play() }
    assertPaused()
  }

  @Test
  fun testPlaySound_whenAudioFocusRequestDelays() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_DELAYED

    playerManager.play("test")
    verify(exactly = 0) { players.getValue("test").play() }
    assertPausedIndefinitely()

    // finally grant audio focus request
    playerManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    verify(exactly = 1) { players.getValue("test").play() }
    assertEquals(PlaybackStateCompat.STATE_PLAYING, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testPlaySound_whenAudioFocusRequestGrants() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    playerManager.play("test")
    verify(exactly = 1) { players.getValue("test").play() }
    assertEquals(PlaybackStateCompat.STATE_PLAYING, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testStopSound_whenSoundIsNotPlaying() {
    mockkStatic(AudioManagerCompat::class)
    // should do noop if player is not present in players state
    playerManager.stop("test-x")  // stop something other than "test
    assertEquals(PlaybackStateCompat.STATE_STOPPED, ShadowMediaSessionCompat.getLastPlaybackState())

    // shouldn't abandon audio focus since players is not empty
    verify(exactly = 0) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
  }

  @Test
  fun testStopSound_whenSoundIsPlaying() {
    mockkStatic(AudioManagerCompat::class)
    val mockPlayer = players.getValue("test")
    playerManager.stop("test")
    verify(exactly = 1) { mockPlayer.stop() }
    assertEquals(PlaybackStateCompat.STATE_STOPPED, ShadowMediaSessionCompat.getLastPlaybackState())
    assertEquals(0, players.size)
    // should abandon audio focus since players is empty
    verify(exactly = 1) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
  }

  @Test
  fun testStopPlayback() {
    mockkStatic(AudioManagerCompat::class)
    val mockPlayer = players.getValue("test")
    playerManager.stop()

    verify(exactly = 1) { mockPlayer.stop() }
    assertEquals(PlaybackStateCompat.STATE_STOPPED, ShadowMediaSessionCompat.getLastPlaybackState())
    assertEquals(0, players.size)
    // should abandon audio focus
    verify(exactly = 1) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
  }

  @Test
  fun testStopPlayback_whenIgnoringAudioFocus() {
    mockkStatic(AudioManagerCompat::class)
    every { settingsRepository.shouldIgnoreAudioFocusChanges() } returns true

    val mockPlayer = players.getValue("test")
    playerManager.stop()

    verify(exactly = 1) { mockPlayer.stop() }
    verify(exactly = 0) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
  }


  @Test
  fun testPause() {
    playerManager.pause()
    assertEquals(1, players.size)
    verify(exactly = 1) { players.getValue("test").pause() }
    assertPaused()
  }

  @Test
  fun testResumePlayback_whenAudioFocusRequestFails() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

    playerManager.resume()
    verify(exactly = 0) { players.getValue("test").play() }
    assertPaused()
  }

  @Test
  fun testResumePlayback_whenAudioFocusRequestDelays() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_DELAYED

    playerManager.resume()
    verify(exactly = 0) { players.getValue("test").play() }
    assertPausedIndefinitely()
  }

  @Test
  fun testResumePlayback_whenAudioFocusRequestGrants() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    playerManager.resume()
    verify(exactly = 1) { players.getValue("test").play() }
    assertEquals(PlaybackStateCompat.STATE_PLAYING, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testCleanup() {
    playerManager.cleanup()
    // should stop all players
    assertEquals(0, players.size)
    verify(exactly = 1) {
      // should clear session callbacks
      mockCastApiProvider.unregisterSessionListener(any())
    }
  }

  @Test
  fun testUpdatePlaybackStrategies() {
    val listenerSlot = slot<CastApiProvider.SessionListener>()
    verify(exactly = 1) {
      mockCastApiProvider.registerSessionListener(capture(listenerSlot))
    }

    val mockPlaybackStrategy = mockk<PlaybackStrategyFactory>(relaxed = true)
    val mockVolumeProvider = mockk<VolumeProviderCompat> {
      every { volumeProvider } returns mockk<VolumeProvider>(relaxed = true)
    }

    every { mockCastApiProvider.getPlaybackStrategyFactory(any()) } returns mockPlaybackStrategy
    every { mockCastApiProvider.getVolumeProvider() } returns mockVolumeProvider
    listenerSlot.captured.onCastSessionBegin() // invoke the session begin callback
    verify(exactly = 1) { players.getValue("test").updatePlaybackStrategy(mockPlaybackStrategy) }
    assertEquals(mockVolumeProvider, ShadowMediaSessionCompat.currentVolumeProvider)
    clearMocks(mockPlaybackStrategy, players.getValue("test"))

    listenerSlot.captured.onCastSessionEnd()
    verify(exactly = 1) { players.getValue("test").updatePlaybackStrategy(any()) }
    verify { mockPlaybackStrategy wasNot called }
  }

  @Test
  fun testPlayPreset() {
    mockkObject(Sound.Companion)
    every { Sound.get(any()) } returns mockk(relaxed = true) { every { isLooping } returns true }
    players["test-2"] = mockk(relaxed = true) { every { soundKey } returns "test-2" }
    val mockPreset = mockk<Preset> {
      every { playerStates } returns arrayOf(
        mockk(relaxed = true) { every { soundKey } returns "test-2" },
        mockk(relaxed = true) { every { soundKey } returns "test-3" }
      )
    }

    playerManager.playPreset(mockPreset)
    assertFalse(players.contains("test"))
    assertTrue(players.contains("test-2"))
    assertTrue(players.contains("test-3"))
  }

  /**
   * Essentially, the issue requested to not dampen the sound of other music players when playing
   * sounds in Noice. The dampening is done by the Android Framework after Noice requests audio
   * focus and is out of the control of the app. But the issue also mentioned that it doesn't happen
   * if the sound is played once, stopped and then played again.
   *
   * On investigating further, the issue is that the audio focus request was not issued again if
   * [PlayerManager] had abandoned it previously.
   */
  @Test
  fun testIssue462() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    val player = players.getValue("test")

    playerManager.play(player.soundKey)
    playerManager.stop(player.soundKey)
    players[player.soundKey] = player // stopping the player will remove it from players map.
    playerManager.play(player.soundKey)
    playerManager.stop(player.soundKey)
    verify(exactly = 2) {
      player.play()
      player.stop()

      AudioManagerCompat.requestAudioFocus(any(), any())
      AudioManagerCompat.abandonAudioFocusRequest(any(), any())
    }
  }

  @Test
  fun testCallPlaybackUpdateListener() {
    val listener = mockk<PlaybackUpdateListener>(relaxed = true)
    playerManager.setPlaybackUpdateListener(listener)
    playerManager.callPlaybackUpdateListener()
    verify(exactly = 1) { listener.invoke(any(), any()) }
  }

  @Test
  fun testSetAudioUsage() {
    val mockPlayer = players.getValue("test")
    val audioUsage = AudioAttributesCompat.USAGE_ALARM
    playerManager.setAudioUsage(audioUsage)
    verify(exactly = 1) {
      mockPlayer.setAudioAttributes(withArg {
        assertEquals(audioUsage, it.usage)
      })
    }
  }
}
