package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import androidx.media.AudioAttributesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.exoplayer2.ExoPlayer
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep


@RunWith(AndroidJUnit4::class)
class PlaybackTest {

  private lateinit var context: Context
  private lateinit var audioAttributes: AudioAttributesCompat
  private lateinit var loopingSound: Sound
  private lateinit var nonLoopingSound: Sound

  @Before
  fun setup() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    audioAttributes = AudioAttributesCompat.Builder().build()
    loopingSound = requireNotNull(Sound.LIBRARY["birds"])
    nonLoopingSound = requireNotNull(Sound.LIBRARY["rolling_thunder"])
  }

  @Test
  fun testLoopingPlayback() {
    var p: Playback? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      p = Playback(context, loopingSound, audioAttributes)
    }

    // should be set to looping on object initialization
    assertEquals(ExoPlayer.REPEAT_MODE_ONE, requireNotNull(p).player.repeatMode)
    // shouldn't be playing by default
    assertFalse(requireNotNull(p).isPlaying)
    assertFalse(requireNotNull(p).player.playWhenReady)

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      requireNotNull(p).play()
    }

    // should start playing the MediaPlayer
    assertTrue(requireNotNull(p).isPlaying)
    assertTrue(requireNotNull(p).player.playWhenReady)
  }

  @Test
  fun testNonLoopingPlayback() {
    var p: Playback? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      p = Playback(context, nonLoopingSound, audioAttributes)
    }

    // should be set to looping on object initialization
    assertNotEquals(ExoPlayer.REPEAT_MODE_ONE, requireNotNull(p).player.repeatMode)
    // shouldn't be playing by default
    assertFalse(requireNotNull(p).isPlaying)
    assertFalse(requireNotNull(p).player.playWhenReady)
    // shouldn't add any callbacks to the handler
    assertFalse(requireNotNull(p).handler.hasCallbacks(requireNotNull(p)))

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      requireNotNull(p).play()
    }

    // should start playing the MediaPlayer
    assertTrue(requireNotNull(p).isPlaying)
    assertTrue(requireNotNull(p).player.playWhenReady)
    // should add itself as a callback to the handler
    assertTrue(requireNotNull(p).handler.hasCallbacks(requireNotNull(p)))
  }

  @Test
  fun testStopLoopingPlayback() {
    var p: Playback? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      p = Playback(context, loopingSound, audioAttributes)
      requireNotNull(p).play()
    }

    // should start playing the MediaPlayer
    assertTrue(requireNotNull(p).isPlaying)
    assertTrue(requireNotNull(p).player.playWhenReady)

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      requireNotNull(p).stop(false)
    }

    // should stop
    assertFalse(requireNotNull(p).isPlaying)
    assertFalse(requireNotNull(p).player.playWhenReady)
  }

  @Test
  fun testStopNonLoopingPlayback() {
    var p: Playback? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      p = Playback(context, nonLoopingSound, audioAttributes)
      requireNotNull(p).play()
    }

    // should start playing the MediaPlayer
    assertTrue(requireNotNull(p).isPlaying)
    assertTrue(requireNotNull(p).player.playWhenReady)
    // should add itself as a callback to the handler
    assertTrue(requireNotNull(p).handler.hasCallbacks(requireNotNull(p)))

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      requireNotNull(p).stop(false)
    }

    // should stop
    assertFalse(requireNotNull(p).isPlaying)
    assertFalse(requireNotNull(p).player.playWhenReady)
    // should remove itself as a callback from the handler
    assertFalse(requireNotNull(p).handler.hasCallbacks(requireNotNull(p)))
  }

  @Test
  fun testFadeInOutTransitions() {
    var p: Playback? = null
    val defaultVolume = Playback.DEFAULT_VOLUME.toFloat() / Playback.MAX_VOLUME
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      p = Playback(context, loopingSound, audioAttributes)
      requireNotNull(p).play()
      assertNotEquals(defaultVolume, requireNotNull(p).player.volume)
    }

    // wait for fade-in to finish
    sleep(600L)
    assertEquals(defaultVolume, requireNotNull(p).player.volume)

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      requireNotNull(p).stop(true)
      assertNotEquals(0f, requireNotNull(p).player.volume)
    }

    // wait for fade-out to finish
    sleep(600L)
    assertEquals(0f, requireNotNull(p).player.volume)
  }

  @Test
  fun testJsonSerializationDeserialization() {
    val gson = GsonBuilder()
      .excludeFieldsWithoutExposeAnnotation()
      .create()

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val p = Playback(context, nonLoopingSound, audioAttributes)
      p.timePeriod = 120
      p.setVolume(14)

      val json = gson.toJson(p)
      val anotherP = gson.fromJson(json, Playback::class.java)
      assertEquals(p.volume, anotherP.volume)
      assertEquals(p.timePeriod, anotherP.timePeriod)
    }
  }
}
