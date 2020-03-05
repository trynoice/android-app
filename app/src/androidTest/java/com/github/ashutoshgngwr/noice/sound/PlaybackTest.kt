package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep


@RunWith(AndroidJUnit4::class)
class PlaybackTest {

  private lateinit var context: Context
  private lateinit var audioManager: AudioManager
  private lateinit var audioAttributes: AudioAttributesCompat
  private lateinit var loopingSound: Sound
  private lateinit var nonLoopingSound: Sound

  @Before
  fun setup() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioAttributes = AudioAttributesCompat.Builder().build()
    loopingSound = requireNotNull(Sound.LIBRARY["birds"])
    nonLoopingSound = requireNotNull(Sound.LIBRARY["rolling_thunder"])
  }

  @Test
  @UiThreadTest
  fun testLoopingPlayback() {
    val p = Playback(context, loopingSound, audioManager.generateAudioSessionId(), audioAttributes)

    // should be set to looping on object initialization
    assertTrue(p.mediaPlayer.isLooping)
    // shouldn't be playing by default
    assertFalse(p.isPlaying)
    assertFalse(p.mediaPlayer.isPlaying)
    p.play()

    // should start playing the MediaPlayer
    assertTrue(p.isPlaying)
    assertTrue(p.mediaPlayer.isPlaying)
  }

  @Test
  @UiThreadTest
  fun testNonLoopingPlayback() {
    val p = Playback(
      context,
      nonLoopingSound,
      audioManager.generateAudioSessionId(),
      audioAttributes
    )

    // should be set to looping on object initialization
    assertFalse(p.mediaPlayer.isLooping)
    // shouldn't be playing by default
    assertFalse(p.isPlaying)
    assertFalse(p.mediaPlayer.isPlaying)
    // shouldn't add any callbacks to the handler
    assertFalse(p.handler.hasCallbacks(p))

    p.play()

    // should start playing the MediaPlayer
    assertTrue(p.isPlaying)
    assertTrue(p.mediaPlayer.isPlaying)
    // should add itself as a callback to the handler
    assertTrue(p.handler.hasCallbacks(p))

    // wait for the sound to finish playing once
    sleep(p.mediaPlayer.duration.toLong() + 500L)

    // should stop the media player's playback. this should always pass since minimum delay for
    // replaying a sound is always more than a few seconds. Also playback's state should be playing.
    assertTrue(p.isPlaying)
    assertFalse(p.mediaPlayer.isPlaying)
  }

  @Test
  @UiThreadTest
  fun testStopLoopingPlayback() {
    val p = Playback(context, loopingSound, audioManager.generateAudioSessionId(), audioAttributes)
    p.play()

    // should start playing the MediaPlayer
    assertTrue(p.isPlaying)
    assertTrue(p.mediaPlayer.isPlaying)

    p.stop()

    // should stop
    assertFalse(p.isPlaying)
    assertFalse(p.mediaPlayer.isPlaying)
  }

  @Test
  @UiThreadTest
  fun testStopNonLoopingPlayback() {
    val p = Playback(
      context,
      nonLoopingSound,
      audioManager.generateAudioSessionId(),
      audioAttributes
    )

    p.play()

    // should start playing the MediaPlayer
    assertTrue(p.isPlaying)
    assertTrue(p.mediaPlayer.isPlaying)
    // should add itself as a callback to the handler
    assertTrue(p.handler.hasCallbacks(p))

    p.stop()

    // should stop
    assertFalse(p.isPlaying)
    assertFalse(p.mediaPlayer.isPlaying)
    // should remove itself as a callback from the handler
    assertFalse(p.handler.hasCallbacks(p))
  }
}
