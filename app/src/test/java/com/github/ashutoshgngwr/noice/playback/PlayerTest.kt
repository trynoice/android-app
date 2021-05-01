package com.github.ashutoshgngwr.noice.playback

import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategy
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategyFactory
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class PlayerTest {

  private lateinit var mockSound: Sound
  private lateinit var mockPlaybackStrategy: PlaybackStrategy
  private lateinit var player: Player

  @Before
  fun setup() {
    mockkObject(Sound.Companion)
    mockSound = mockk(relaxed = true)
    mockPlaybackStrategy = mockk(relaxed = true)
    every { Sound.get("test") } returns mockSound
    player = Player("test", mockk {
      every { newInstance(any()) } returns mockPlaybackStrategy
    })

    // clear calls that may have been invoked during player initialization.
    clearMocks(mockSound, mockPlaybackStrategy)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testSetVolume() {
    player.setVolume(16)
    verify(exactly = 1) { mockPlaybackStrategy.setVolume(16f / Player.MAX_VOLUME) }
    assertEquals(16, player.volume)
  }

  @Test
  fun testPlay_withLoopingSound() {
    every { mockSound.isLooping } returns true
    player.play()
    verify(exactly = 1) { mockPlaybackStrategy.play() }
  }

  @Test
  fun testPlay_withNonLoopingSound() {
    every { mockSound.isLooping } returns false
    player.play()

    verify { mockPlaybackStrategy.play() }
    clearMocks(mockPlaybackStrategy)

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks() // run the delayed callback
    verify(exactly = 1) { mockPlaybackStrategy.play() }
  }

  @Test
  fun testPause_withLoopingSound() {
    every { mockSound.isLooping } returns true
    player.pause()
    verify(exactly = 1) { mockPlaybackStrategy.pause() }
  }

  @Test
  fun testPause_withNonLoopingSound() {
    every { mockSound.isLooping } returns false
    player.pause()
    verify(exactly = 1) { mockPlaybackStrategy.pause() }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks() // try to invoke delayed play callback
    verify(exactly = 0) { mockPlaybackStrategy.play() } // need no more interactions
  }

  @Test
  fun testStop_withLoopingSound() {
    every { mockSound.isLooping } returns true
    player.stop()
    verify(exactly = 1) { mockPlaybackStrategy.stop() }
  }

  @Test
  fun testStop_withNonLoopingSound() {
    every { mockSound.isLooping } returns false
    player.stop()
    verify(exactly = 1) { mockPlaybackStrategy.stop() }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks() // try to invoke delayed play callback
    verify(exactly = 0) { mockPlaybackStrategy.play() }
  }

  @Test
  fun testUpdatePlayerStrategy_onStoppedPlayback() {
    val strategy2 = mockk<PlaybackStrategy>(relaxed = true)
    mockk<PlaybackStrategyFactory> {
      every { newInstance(any()) } returns strategy2
      player.updatePlaybackStrategy(this)
    }

    verifySequence {
      mockPlaybackStrategy.pause()
      mockPlaybackStrategy.stop()
      strategy2.setVolume(any())
    }
  }

  @Test
  fun testUpdatePlayerStrategy_onOngoingPlayback_withNonLoopingSound() {
    player.play() // ensure that it is playing

    val strategy2 = mockk<PlaybackStrategy>(relaxed = true)
    every { mockSound.isLooping } returns false
    mockk<PlaybackStrategyFactory> {
      every { newInstance(any()) } returns strategy2
      player.updatePlaybackStrategy(this)
    }

    verifySequence {
      mockPlaybackStrategy.play() // from initial play call
      mockPlaybackStrategy.pause()
      mockPlaybackStrategy.stop()
      strategy2.setVolume(any())
    }

    clearMocks(strategy2) // clear previous calls

    // play is called with delayed callback for non-looping sounds
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verifySequence {
      strategy2.play()
    }
  }

  @Test
  fun testUpdatePlayerStrategy_onOngoingPlayback_withLoopingSound() {
    player.play() // ensure that it is playing

    val strategy2 = mockk<PlaybackStrategy>(relaxed = true)
    every { mockSound.isLooping } returns true
    mockk<PlaybackStrategyFactory> {
      every { newInstance(any()) } returns strategy2
      player.updatePlaybackStrategy(this)
    }

    verifySequence {
      mockPlaybackStrategy.play() // from initial play call
      mockPlaybackStrategy.pause()
      mockPlaybackStrategy.stop()
      strategy2.setVolume(any())
      strategy2.play()
    }
  }
}
