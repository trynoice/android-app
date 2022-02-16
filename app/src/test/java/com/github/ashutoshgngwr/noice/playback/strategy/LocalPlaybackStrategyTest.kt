package com.github.ashutoshgngwr.noice.playback.strategy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class LocalPlaybackStrategyTest {

  private lateinit var mockPlayer: SimpleExoPlayer
  private lateinit var players: List<SimpleExoPlayer>

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var playbackStrategy: LocalPlaybackStrategy

  @Before
  fun setup() {
    val context: Context = ApplicationProvider.getApplicationContext()
    mockPlayer = mockk(relaxed = true)
    players = listOf(mockPlayer)
    playbackStrategy = LocalPlaybackStrategy(
      context,
      mockk(relaxed = true),
      mockk(relaxed = true),
      mockk(relaxed = true),
    )

    MockKAnnotations.init(this)
  }

  @Test
  fun testSetVolume() {
    playbackStrategy.setVolume(1f)
    verify(exactly = 1) { mockPlayer.volume = 1f }
  }

  @Test
  fun testPlay_onStoppedPlayback_withLoopingSound() {
    // should fade in looping sounds
    every { mockPlayer.playWhenReady } returns true
    every { mockPlayer.isPlaying } returns false
    every { mockPlayer.repeatMode } returns ExoPlayer.REPEAT_MODE_ONE
    playbackStrategy.setVolume(1f)
    playbackStrategy.play()
    verify(exactly = 1) { mockPlayer.playWhenReady = true }
  }

  @Test
  fun testPlay_onStoppedPlayback_withNonLoopingSound() {
    // should start playback without fade
    every { mockPlayer.isPlaying } returns false
    every { mockPlayer.repeatMode } returns ExoPlayer.REPEAT_MODE_OFF
    playbackStrategy.play()
    verifyOrder {
      mockPlayer.seekTo(0)
      mockPlayer.playWhenReady = true
    }
  }

  @Test
  fun testPause() {
    playbackStrategy.pause()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify(exactly = 1) { mockPlayer.playWhenReady = false }
  }

  @Test
  fun testStop_withStoppedPlayback() {
    every { mockPlayer.playWhenReady } returns false
    playbackStrategy.stop()
    verify(exactly = 0) { mockPlayer.playWhenReady = any() }
  }

  @Test
  fun testStop_onOngoingPlayback() {
    every { mockPlayer.playWhenReady } returns true
    every { mockPlayer.isPlaying } returns true
    every { mockPlayer.volume } returns 1f
    playbackStrategy.stop()

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify(exactly = 1) {
      mockPlayer.playWhenReady = false
      mockPlayer.release()
    }
  }
}
