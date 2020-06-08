package com.github.ashutoshgngwr.noice.sound.player.adapter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocalPlayerAdapterTest {

  @RelaxedMockK
  private lateinit var exoPlayer: SimpleExoPlayer

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var playerAdapter: LocalPlayerAdapter

  @Before
  fun setup() {
    val context: Context = ApplicationProvider.getApplicationContext()
    playerAdapter = LocalPlayerAdapter(context, mockk(relaxed = true), mockk(relaxed = true))
    MockKAnnotations.init(this)
  }

  @Test
  fun testSetVolume() {
    playerAdapter.setVolume(1f)
    verify(exactly = 1) { exoPlayer.volume = 1f }
  }

  @Test
  fun testPlay_onOngoingPlayback() {
    every { exoPlayer.playWhenReady } returns true

    playerAdapter.play()
    verify(exactly = 0) { exoPlayer.playWhenReady = any() }
  }

  @Test
  fun testPlay_onStoppedPlayback_withLoopingSound() {
    // should fade in looping sounds
    every { exoPlayer.playWhenReady } returns false
    every { exoPlayer.repeatMode } returns ExoPlayer.REPEAT_MODE_ONE
    every { exoPlayer.volume } returns 1f
    playerAdapter.play()

    shadowOf(playerAdapter.transitionTicker).also {
      it.invokeTick(100)
      it.invokeFinish()
    }

    verify(exactly = 1) { exoPlayer.playWhenReady = true }
    val volumeSlots = mutableListOf<Float>()
    verify(exactly = 3) { exoPlayer.volume = capture(volumeSlots) }
    assertTrue("volume should increase with each step", volumeSlots[0] < volumeSlots[1])
    assertEquals("volume should be set to desired value on finish", 1f, volumeSlots[2])
  }

  @Test
  fun testPlay_onStoppedPlayback_withNonLoopingSound() {
    // should start playback without fade
    every { exoPlayer.playWhenReady } returns false
    every { exoPlayer.repeatMode } returns ExoPlayer.REPEAT_MODE_OFF
    playerAdapter.play()
    verify(exactly = 1) { exoPlayer.playWhenReady = true }
  }

  @Test
  fun testPause() {
    playerAdapter.pause()
    verify(exactly = 1) { exoPlayer.playWhenReady = false }
  }

  @Test
  fun testStop_withStoppedPlayback() {
    every { exoPlayer.playWhenReady } returns false
    playerAdapter.stop()
    verify(exactly = 0) { exoPlayer.playWhenReady = any() }
  }

  @Test
  fun testStop_onOngoingPlayback() {
    every { exoPlayer.playWhenReady } returns true
    every { exoPlayer.volume } returns 1f
    playerAdapter.stop()

    shadowOf(playerAdapter.transitionTicker).also {
      it.invokeTick(100)
      it.invokeFinish()
    }

    verify(exactly = 1) {
      exoPlayer.playWhenReady = false
      exoPlayer.release()
    }

    val volumeSlots = mutableListOf<Float>()
    verify(atLeast = 3) { exoPlayer.volume = capture(volumeSlots) }
    assertTrue("volume should decrease with each step", volumeSlots[0] > volumeSlots[1])
    assertEquals("volume should be set to desired value on finish", 0f, volumeSlots[2])
  }
}
