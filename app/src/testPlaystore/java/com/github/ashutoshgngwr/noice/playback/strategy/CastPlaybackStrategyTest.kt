package com.github.ashutoshgngwr.noice.playback.strategy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.model.Sound
import com.google.android.gms.cast.framework.CastSession
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.mockk
import io.mockk.slot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.skyscreamer.jsonassert.JSONAssert

@RunWith(RobolectricTestRunner::class)
class CastPlaybackStrategyTest {

  private val namespace = "test"

  private lateinit var context: Context
  private lateinit var session: CastSession
  private lateinit var sound: Sound

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var playbackStrategy: CastPlaybackStrategy

  private lateinit var jsonSlot: CapturingSlot<String>

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    jsonSlot = slot()
    sound = mockk(relaxed = true) {
      every { src } returns arrayOf("test")
      every { isLooping } returns false
    }

    session = mockk(relaxed = true) {
      every { sendMessage(namespace, capture(jsonSlot)) } returns mockk()
    }

    MockKAnnotations.init(this)
  }

  @Test
  fun testInit() {
    // should send ACTION_CREATE on init
    val expectedJSON = """{
      "src": ["test"],
      "isLooping": false,
      "volume": 0.0,
      "action": "create",
      "fadeInDuration": 1000
    }""".trimIndent()

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testSetVolume() {
    playbackStrategy.setVolume(1f)
    val expectedJSON = """{
      "src": ["test"],
      "isLooping": false,
      "volume": 1.0,
      "fadeInDuration": 1000
    }""".trimIndent()

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testPlay_withLoopingSound() {
    every { sound.isLooping } returns true

    playbackStrategy.play()
    val expectedJSON = """{
      "src": ["test"],
      "isLooping": true,
      "volume": 0.0,
      "action": "play",
      "fadeInDuration": 1000
    }""".trimIndent()

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testPlay_withNonLoopingSound() {
    playbackStrategy.play()
    val expectedJSON = """{
      "src": ["test"],
      "isLooping": false,
      "volume": 0.0,
      "action": "play",
      "fadeInDuration": 1000
    }""".trimIndent()

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testPause() {
    playbackStrategy.pause()
    val expectedJSON = """{
      "src": ["test"],
      "isLooping": false,
      "volume": 0.0,
      "action": "pause",
      "fadeInDuration": 1000
    }""".trimIndent()

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testStop() {
    playbackStrategy.stop()
    val expectedJSON = """{
      "src": ["test"],
      "isLooping": false,
      "volume": 0.0,
      "action": "stop",
      "fadeInDuration": 1000
    }""".trimIndent()

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }
}
