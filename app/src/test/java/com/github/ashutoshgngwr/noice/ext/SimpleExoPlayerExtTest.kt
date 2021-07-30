package com.github.ashutoshgngwr.noice.ext

import com.google.android.exoplayer2.SimpleExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class SimpleExoPlayerExtTest {

  private lateinit var player: SimpleExoPlayer

  @Before
  fun setup() {
    player = mockk(relaxed = true)
  }

  @Test
  fun testFade() {
    every { player.playWhenReady } returns true

    val callback: () -> Unit = mockk(relaxed = true)
    player.fade(0f, 1f, 1000L, callback)

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    val volumeSlots = mutableListOf<Float>()
    verify(atLeast = 3) { player.volume = capture(volumeSlots) }
    Assert.assertTrue(
      "volume should increase with each step",
      volumeSlots.first() < volumeSlots.last()
    )

    Assert.assertEquals(
      "volume should be set to desired value on finish",
      1f, volumeSlots.last(),
    )

    verify(exactly = 1) { callback.invoke() }
  }
}
