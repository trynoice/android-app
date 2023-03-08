package com.github.ashutoshgngwr.noice.engine

import androidx.test.core.app.ApplicationProvider
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.robolectric.RobolectricUtil
import com.google.android.exoplayer2.testutil.FakeMediaSourceFactory
import com.google.android.exoplayer2.testutil.TestExoPlayerBuilder
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
class MediaPlayerTest {

  private lateinit var exoPlayer: ExoPlayer
  private lateinit var mediaPlayer: MediaPlayer

  @Before
  fun setUp() {
    exoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
      .setMediaSourceFactory(FakeMediaSourceFactory())
      .build()

    mediaPlayer = MediaPlayer(exoPlayer)
  }

  @Test
  fun initialState() {
    assertEquals(MediaPlayer.State.PAUSED, mediaPlayer.state)
  }

  @Test
  fun play_AddToPlaylist_clearPlaylist() {
    mediaPlayer.play()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.IDLE }

    mediaPlayer.addToPlaylist("https://cdn.test/uri")
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.BUFFERING }
    assertEquals(1, exoPlayer.mediaItemCount)
    assertEquals(1, mediaPlayer.getRemainingItemCount())

    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.PLAYING }
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.IDLE }
    assertEquals(0, mediaPlayer.getRemainingItemCount())

    mediaPlayer.addToPlaylist("https://cdn.test/uri")
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.BUFFERING }
    assertEquals(1, mediaPlayer.getRemainingItemCount())

    mediaPlayer.clearPlaylist()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.IDLE }
    assertEquals(0, mediaPlayer.getRemainingItemCount())
  }

  @Test
  fun addToPlaylist_Play_Pause_Play_AndStop() {
    mediaPlayer.addToPlaylist("https://cdn.test/uri")
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.PAUSED }

    mediaPlayer.play()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.BUFFERING }
    assertEquals(1, exoPlayer.mediaItemCount)
    assertEquals(1, mediaPlayer.getRemainingItemCount())

    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.PLAYING }

    mediaPlayer.pause()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.PAUSED }
    assertFalse(exoPlayer.playWhenReady)

    mediaPlayer.play()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.PLAYING }
    assertTrue(exoPlayer.isPlaying)

    mediaPlayer.stop()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.STOPPED }
  }

  @Test
  fun addListenerAndRemoveListener() {
    val mockListener = mockk<MediaPlayer.Listener>(relaxed = true)
    mediaPlayer.addListener(mockListener)

    mediaPlayer.play()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.IDLE }
    verify(exactly = 1) { mockListener.onMediaPlayerStateChanged(MediaPlayer.State.IDLE) }

    mediaPlayer.addToPlaylist("https://cdn.test/uri")
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.BUFFERING }
    verify(exactly = 1) { mockListener.onMediaPlayerItemTransition() }

    clearMocks(mockListener)
    mediaPlayer.removeListener(mockListener)
    mediaPlayer.pause()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.PAUSED }
    verify(exactly = 0) { mockListener.onMediaPlayerStateChanged(MediaPlayer.State.PAUSED) }
  }

  @Test
  fun fadeTo() {
    mediaPlayer.setVolume(0F)
    mediaPlayer.addToPlaylist("https://cdn.test/uri")
    mediaPlayer.play()
    RobolectricUtil.runMainLooperUntil { mediaPlayer.state == MediaPlayer.State.PLAYING }
    assertEquals(0F, exoPlayer.volume)

    val mockCallback = mockk<() -> Unit>(relaxed = true)
    mediaPlayer.fadeTo(1F, 100.milliseconds, mockCallback)
    RobolectricUtil.runMainLooperUntil { exoPlayer.volume <= 0.5F }
    verify(exactly = 0) { mockCallback.invoke() }
    RobolectricUtil.runMainLooperUntil { exoPlayer.volume == 1F }
    verify(exactly = 1) { mockCallback.invoke() }

    clearMocks(mockCallback)
    mediaPlayer.fadeTo(0F, 100.milliseconds, mockCallback)
    RobolectricUtil.runMainLooperUntil { exoPlayer.volume >= 0.5F }
    verify(exactly = 0) { mockCallback.invoke() }
    RobolectricUtil.runMainLooperUntil { exoPlayer.volume == 0F }
    verify(exactly = 1) { mockCallback.invoke() }
  }
}
