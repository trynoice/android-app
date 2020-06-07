package com.github.ashutoshgngwr.noice.sound

import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapter
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapterFactory
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class PlayerTest {

  private lateinit var mockSound: Sound
  private lateinit var mockPlayerAdapter: PlayerAdapter
  private lateinit var player: Player

  @Before
  fun setup() {
    mockSound = mockk(relaxed = true)
    mockPlayerAdapter = mockk(relaxed = true)
    player = Player(mockSound, mockk {
      every { newPlayerAdapter(any()) } returns mockPlayerAdapter
    })

    // clear calls that may have been invoked during player initialization.
    clearMocks(mockSound, mockPlayerAdapter)
  }

  @Test
  fun testSetVolume() {
    player.setVolume(16)
    verify(exactly = 1) { mockPlayerAdapter.setVolume(16f / Player.MAX_VOLUME) }
    assertEquals(16, player.volume)
  }

  @Test
  fun testPlay_withLoopingSound() {
    every { mockSound.isLoopable } returns true
    player.play()
    verify(exactly = 1) { mockPlayerAdapter.play() }
  }

  @Test
  fun testPlay_withNonLoopingSound() {
    every { mockSound.isLoopable } returns false
    player.play()

    verify { mockPlayerAdapter.play() }
    clearMocks(mockPlayerAdapter)

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks() // run the delayed callback
    verify(exactly = 1) { mockPlayerAdapter.play() }
  }

  @Test
  fun testPause_withLoopingSound() {
    every { mockSound.isLoopable } returns true
    player.pause()
    verify(exactly = 1) { mockPlayerAdapter.pause() }
  }

  @Test
  fun testPause_withNonLoopingSound() {
    every { mockSound.isLoopable } returns false
    player.pause()
    verify(exactly = 1) { mockPlayerAdapter.pause() }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks() // try to invoke delayed play callback
    verify(exactly = 0) { mockPlayerAdapter.play() } // need no more interactions
  }

  @Test
  fun testStop_withLoopingSound() {
    every { mockSound.isLoopable } returns true
    player.stop()
    verify(exactly = 1) { mockPlayerAdapter.stop() }
  }

  @Test
  fun testStop_withNonLoopingSound() {
    every { mockSound.isLoopable } returns false
    player.stop()
    verify(exactly = 1) { mockPlayerAdapter.stop() }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks() // try to invoke delayed play callback
    verify(exactly = 0) { mockPlayerAdapter.play() }
  }

  @Test
  fun testRecreatePlayerAdapter_onStoppedPlayback() {
    val adapter2 = mockk<PlayerAdapter>(relaxed = true)
    mockk<PlayerAdapterFactory> {
      every { newPlayerAdapter(any()) } returns adapter2
      player.recreatePlayerAdapter(this)
    }

    verifySequence {
      mockPlayerAdapter.pause()
      mockPlayerAdapter.stop()
      adapter2.setVolume(any())
    }
  }

  @Test
  fun testRecreatePlayerAdapter_onOngoingPlayback_withNonLoopingSound() {
    player.play() // ensure that it is playing

    val adapter2 = mockk<PlayerAdapter>(relaxed = true)
    every { mockSound.isLoopable } returns false
    mockk<PlayerAdapterFactory>() {
      every { newPlayerAdapter(any()) } returns adapter2
      player.recreatePlayerAdapter(this)
    }

    verifySequence {
      mockPlayerAdapter.play() // from initial play call
      mockPlayerAdapter.pause()
      mockPlayerAdapter.stop()
      adapter2.setVolume(any())
    }

    clearMocks(adapter2) // clear previous calls

    // play is called with delayed callback for non-looping sounds
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verifySequence {
      adapter2.play()
    }
  }

  @Test
  fun testRecreatePlayerAdapter_onOngoingPlayback_withLoopingSound() {
    player.play() // ensure that it is playing

    val adapter2 = mockk<PlayerAdapter>(relaxed = true)
    every { mockSound.isLoopable } returns true
    mockk<PlayerAdapterFactory>() {
      every { newPlayerAdapter(any()) } returns adapter2
      player.recreatePlayerAdapter(this)
    }

    verifySequence {
      mockPlayerAdapter.play() // from initial play call
      mockPlayerAdapter.pause()
      mockPlayerAdapter.stop()
      adapter2.setVolume(any())
      adapter2.play()
    }
  }
}
