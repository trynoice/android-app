package com.github.ashutoshgngwr.noice.cast.player.strategy

import com.github.ashutoshgngwr.noice.sound.Sound
import com.google.android.gms.cast.framework.CastSession
import io.mockk.*
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.skyscreamer.jsonassert.JSONAssert

@RunWith(RobolectricTestRunner::class)
class CastPlaybackStrategyTest {

  private val namespace = "test"

  private lateinit var session: CastSession

  private lateinit var sound: Sound

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var playbackStrategy: CastPlaybackStrategy

  private lateinit var jsonSlot: CapturingSlot<String>

  @Before
  fun setup() {
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
    val expectedJSON = "{" +
      "  \"src\": [\"test\"]," +
      "  \"isLooping\": false," +
      "  \"volume\": 0.0," +
      "  \"action\": \"create\"" +
      "}"

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testSetVolume() {
    playbackStrategy.setVolume(1f)
    val expectedJSON = "{" +
      "  \"src\": [\"test\"]," +
      "  \"isLooping\": false," +
      "  \"volume\": 1.0" +
      "}"

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testPlay_withLoopingSound() {
    every { sound.isLooping } returns true

    playbackStrategy.play()
    val expectedJSON = "{" +
      "  \"src\": [\"test\"]," +
      "  \"isLooping\": true," +
      "  \"volume\": 0.0," +
      "  \"action\": \"play\"" +
      "}"

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testPlay_withNonLoopingSound() {
    playbackStrategy.play()
    val expectedJSON = "{" +
      "  \"src\": [\"test\"]," +
      "  \"isLooping\": false," +
      "  \"volume\": 0.0," +
      "  \"action\": \"play\"" +
      "}"

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testPause() {
    playbackStrategy.pause()
    val expectedJSON = "{" +
      "  \"src\": [\"test\"]," +
      "  \"isLooping\": false," +
      "  \"volume\": 0.0," +
      "  \"action\": \"pause\"" +
      "}"

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }

  @Test
  fun testStop() {
    playbackStrategy.stop()
    val expectedJSON = "{" +
      "  \"src\": [\"test\"]," +
      "  \"isLooping\": false," +
      "  \"volume\": 0.0," +
      "  \"action\": \"stop\"" +
      "}"

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, false)
  }
}
