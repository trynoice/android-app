package com.github.ashutoshgngwr.noice.sound.player

import android.media.AudioManager
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.AudioManagerCompat
import androidx.media.VolumeProviderCompat
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.ShadowMediaSession
import com.github.ashutoshgngwr.noice.ShadowMediaSessionCompat
import com.github.ashutoshgngwr.noice.cast.CastAPIWrapper
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapterFactory
import io.mockk.*
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMediaSession::class, ShadowMediaSessionCompat::class])
class PlayerManagerTest {

  private lateinit var mockCastAPIWrapper: CastAPIWrapper

  private lateinit var players: HashMap<String, Player>

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var playerManager: PlayerManager

  @Before
  fun setup() {
    mockkObject(CastAPIWrapper.Companion)
    mockCastAPIWrapper = mockk(relaxed = true)
    every { CastAPIWrapper.from(any(), any()) } returns mockCastAPIWrapper

    // always have a fake player in manager's state
    players = hashMapOf("test" to mockk(relaxed = true) {
      every { soundKey } returns "test"
    })

    playerManager = PlayerManager(ApplicationProvider.getApplicationContext())
    MockKAnnotations.init(this)
  }

  @Test
  fun testOnAudioFocusChange() {
    // should pause players on focus loss
    playerManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
    verify(exactly = 1) { players.getValue("test").pause() }
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())
    clearMocks(players.getValue("test"))

    // should pause players on focus loss transient
    playerManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
    verify(exactly = 1) { players.getValue("test").pause() }
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())
    clearMocks(players.getValue("test"))

    // should resume players on focus gain
    playerManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    assertEquals(PlaybackStateCompat.STATE_PLAYING, ShadowMediaSessionCompat.getLastPlaybackState())
    verify(exactly = 1) { players.getValue("test").play() }
  }

  @Test
  fun testPlaySound_whenAudioFocusRequestFails() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

    playerManager.play(mockk(relaxed = true) {
      every { key } returns "test"
    })

    verify(exactly = 0) { players.getValue("test").play() }
    assertEquals(PlayerManager.State.PAUSED, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testPlaySound_whenAudioFocusRequestDelays() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_DELAYED

    playerManager.play(mockk(relaxed = true) {
      every { key } returns "test"
    })

    verify(exactly = 0) { players.getValue("test").play() }
    assertEquals(PlayerManager.State.PAUSED, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())

    // finally grant audio focus request
    playerManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    verify(exactly = 1) { players.getValue("test").play() }
    assertEquals(PlayerManager.State.PLAYING, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_PLAYING, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testPlaySound_whenAudioFocusRequestGrants() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    playerManager.play(mockk(relaxed = true) {
      every { key } returns "test"
    })

    verify(exactly = 1) { players.getValue("test").play() }
    assertEquals(PlayerManager.State.PLAYING, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_PLAYING, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testStopSound_whenSoundIsNotPlaying() {
    mockkStatic(AudioManagerCompat::class)
    // should do noop if player is not present in players state
    playerManager.stop(mockk(relaxed = true))
    assertEquals(PlayerManager.State.STOPPED, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_STOPPED, ShadowMediaSessionCompat.getLastPlaybackState())

    // shouldn't abandon audio focus since players is not empty
    verify(exactly = 0) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
  }

  @Test
  fun testStopSound_whenSoundIsPlaying() {
    mockkStatic(AudioManagerCompat::class)
    val mockPlayer = players.getValue("test")
    playerManager.stop(mockk(relaxed = true) {
      every { key } returns "test"
    })

    verify(exactly = 1) { mockPlayer.stop() }
    assertEquals(PlayerManager.State.STOPPED, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_STOPPED, ShadowMediaSessionCompat.getLastPlaybackState())
    assertEquals(0, playerManager.players.size)
    // should abandon audio focus since players is empty
    verify(exactly = 1) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
  }

  @Test
  fun testStopPlayback() {
    mockkStatic(AudioManagerCompat::class)
    val mockPlayer = players.getValue("test")
    playerManager.stop()

    verify(exactly = 1) { mockPlayer.stop() }
    assertEquals(PlayerManager.State.STOPPED, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_STOPPED, ShadowMediaSessionCompat.getLastPlaybackState())
    assertEquals(0, playerManager.players.size)
    // should abandon audio focus
    verify(exactly = 1) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
  }

  @Test
  fun testPausePlayback() {
    mockkStatic(AudioManagerCompat::class)
    playerManager.pause()

    assertEquals(PlayerManager.State.PAUSED, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())
    assertEquals(1, playerManager.players.size)
    verify(exactly = 1) {
      players.getValue("test").pause()
      // should abandon audio focus
      AudioManagerCompat.abandonAudioFocusRequest(any(), any())
    }
  }

  @Test
  fun testResumePlayback_whenAudioFocusRequestFails() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

    playerManager.resume()
    verify(exactly = 0) { players.getValue("test").play() }
    assertEquals(PlayerManager.State.PAUSED, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testResumePlayback_whenAudioFocusRequestDelays() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_DELAYED

    playerManager.resume()
    verify(exactly = 0) { players.getValue("test").play() }
    assertEquals(PlayerManager.State.PAUSED, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_PAUSED, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testResumePlayback_whenAudioFocusRequestGrants() {
    mockkStatic(AudioManagerCompat::class)
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    playerManager.resume()
    verify(exactly = 1) { players.getValue("test").play() }
    assertEquals(PlayerManager.State.PLAYING, playerManager.state)
    assertEquals(PlaybackStateCompat.STATE_PLAYING, ShadowMediaSessionCompat.getLastPlaybackState())
  }

  @Test
  fun testSetVolume() {
    val volume = Random.nextInt(0, 1 + Player.MAX_VOLUME)
    playerManager.setVolume("test", volume)
    verify(exactly = 1) { players.getValue("test").setVolume(volume) }
  }

  @Test
  fun testSetTimePeriod() {
    val timePeriod = Random.nextInt(1, 1 + Player.MAX_TIME_PERIOD)
    playerManager.setTimePeriod("test", timePeriod)
    verify(exactly = 1) { players.getValue("test").timePeriod = timePeriod }
  }

  @Test
  fun testCleanup() {
    playerManager.cleanup()
    // should stop all players
    assertEquals(0, playerManager.players.size)
    verify(exactly = 1) {
      // should clear session callbacks
      mockCastAPIWrapper.clearSessionCallbacks()
    }
  }

  @Test
  fun testPlayerAdapterFactorySwitchover() {
    val beginCallbackSlot = slot<() -> Unit>()
    val endCallbackSlot = slot<() -> Unit>()
    verify(exactly = 1) {
      mockCastAPIWrapper.onSessionBegin(capture(beginCallbackSlot))
      mockCastAPIWrapper.onSessionEnd(capture(endCallbackSlot))
    }

    val mockPlayerAdapterFactory = mockk<PlayerAdapterFactory>(relaxed = true)
    val spyVolumeProvider = spyk<VolumeProviderCompat>()
    every { mockCastAPIWrapper.newCastPlayerAdapterFactory() } returns mockPlayerAdapterFactory
    every { mockCastAPIWrapper.newCastVolumeProvider() } returns spyVolumeProvider
    beginCallbackSlot.invoke() // invoke the session begin callback
    verify(exactly = 1) { players.getValue("test").recreatePlayerAdapter(mockPlayerAdapterFactory) }
    assertEquals(spyVolumeProvider, ShadowMediaSessionCompat.currentVolumeProvider)
    clearMocks(mockPlayerAdapterFactory, players.getValue("test"))

    endCallbackSlot.invoke()
    verify(exactly = 1) { players.getValue("test").recreatePlayerAdapter(any()) }
    verify { mockPlayerAdapterFactory wasNot called }
  }
}
