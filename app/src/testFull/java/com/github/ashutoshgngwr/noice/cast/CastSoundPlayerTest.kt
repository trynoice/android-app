package com.github.ashutoshgngwr.noice.cast

import com.github.ashutoshgngwr.noice.cast.models.EnableSoundPremiumSegmentsEvent
import com.github.ashutoshgngwr.noice.cast.models.PauseSoundEvent
import com.github.ashutoshgngwr.noice.cast.models.PlaySoundEvent
import com.github.ashutoshgngwr.noice.cast.models.SetSoundAudioBitrateEvent
import com.github.ashutoshgngwr.noice.cast.models.SetSoundFadeInDurationEvent
import com.github.ashutoshgngwr.noice.cast.models.SetSoundFadeOutDurationEvent
import com.github.ashutoshgngwr.noice.cast.models.SetSoundVolumeEvent
import com.github.ashutoshgngwr.noice.cast.models.SoundStateChangedEvent
import com.github.ashutoshgngwr.noice.cast.models.StopSoundEvent
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class CastSoundPlayerTest {

  private lateinit var channelMock: CastMessagingChannel
  private lateinit var player: CastSoundPlayer

  @Before
  fun setUp() {
    channelMock = mockk(relaxed = true)
    player = CastSoundPlayer("test-sound", channelMock)
  }

  @Test
  fun setFadeInDuration() {
    listOf(1.minutes, 2.minutes, 3.minutes).forEach { duration ->
      player.setFadeInDuration(duration)
      verify(exactly = 1) {
        channelMock.send(withArg<SetSoundFadeInDurationEvent> { event ->
          assertEquals("test-sound", event.soundId)
          assertEquals(duration.inWholeMilliseconds, event.durationMillis)
        })
      }
    }
  }

  @Test
  fun setFadeOutDuration() {
    listOf(1.minutes, 2.minutes, 3.minutes).forEach { duration ->
      player.setFadeOutDuration(duration)
      verify(exactly = 1) {
        channelMock.send(withArg<SetSoundFadeOutDurationEvent> { event ->
          assertEquals("test-sound", event.soundId)
          assertEquals(duration.inWholeMilliseconds, event.durationMillis)
        })
      }
    }
  }

  @Test
  fun setPremiumSegmentsEnabled() {
    listOf(false, true).forEach { enabled ->
      player.setPremiumSegmentsEnabled(enabled)
      verify(exactly = 1) {
        channelMock.send(withArg<EnableSoundPremiumSegmentsEvent> { event ->
          assertEquals("test-sound", event.soundId)
          assertEquals(enabled, event.isEnabled)
        })
      }
    }
  }

  @Test
  fun setAudioBitrate() {
    listOf("128k", "192k", "256k", "320k").forEach { bitrate ->
      player.setAudioBitrate(bitrate)
      verify(exactly = 1) {
        channelMock.send(withArg<SetSoundAudioBitrateEvent> { event ->
          assertEquals("test-sound", event.soundId)
          assertEquals(bitrate, event.bitrate)
        })
      }
    }
  }

  @Test
  fun setVolume() {
    listOf(0F, 0.25F, 0.5F, 0.75F, 1F).forEach { volume ->
      player.setVolume(volume)
      verify(exactly = 1) {
        channelMock.send(withArg<SetSoundVolumeEvent> { event ->
          assertEquals("test-sound", event.soundId)
          assertEquals(volume, event.volume)
        })
      }
    }
  }

  @Test
  fun play() {
    player.play()
    verify(exactly = 1) {
      channelMock.send(withArg<PlaySoundEvent> { event ->
        assertEquals("test-sound", event.soundId)
      })
    }
  }

  @Test
  fun pause() {
    listOf(false, true).forEach { immediate ->
      player.pause(immediate)
      verify(exactly = 1) {
        channelMock.send(withArg<PauseSoundEvent> { event ->
          assertEquals("test-sound", event.soundId)
          assertEquals(immediate, event.immediate)
        })
      }
    }
  }

  @Test
  fun stop() {
    listOf(false, true).forEach { immediate ->
      player.stop(immediate)
      verify(exactly = 1) {
        channelMock.send(withArg<StopSoundEvent> { event ->
          assertEquals("test-sound", event.soundId)
          assertEquals(immediate, event.immediate)
        })
      }
    }
  }

  @Test
  fun stateChanges() {
    val listenerSlot = slot<CastMessagingChannel.EventListener>()
    verify(exactly = 1) { channelMock.addEventListener(capture(listenerSlot)) }
    assertNotNull(listenerSlot.captured)

    mapOf(
      "buffering" to SoundPlayer.State.BUFFERING,
      "playing" to SoundPlayer.State.PLAYING,
      "pausing" to SoundPlayer.State.PAUSING,
      "paused" to SoundPlayer.State.PAUSED,
      "stopping" to SoundPlayer.State.STOPPING,
      "stopped" to SoundPlayer.State.STOPPED,
    ).forEach { (remoteState, expectedState) ->
      val mockListener = mockk<SoundPlayer.StateChangeListener>(relaxed = true)
      player.setStateChangeListener(mockListener)
      listenerSlot.captured.onEventReceived(SoundStateChangedEvent("test-sound", remoteState))
      assertEquals(expectedState, player.state)
      verify(exactly = 1) { mockListener.onSoundPlayerStateChanged(expectedState) }
    }
  }
}
