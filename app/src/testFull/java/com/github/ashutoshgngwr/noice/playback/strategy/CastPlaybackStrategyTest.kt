package com.github.ashutoshgngwr.noice.playback.strategy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.gms.cast.framework.CastSession
import com.google.gson.Gson
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.skyscreamer.jsonassert.JSONAssert
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class CastPlaybackStrategyTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private val namespace = "test"

  private lateinit var context: Context
  private lateinit var session: CastSession
  private lateinit var sound: Sound
  private lateinit var mockSettingsRepository: SettingsRepository
  private lateinit var playbackStrategy: CastPlaybackStrategy
  private lateinit var jsonSlot: CapturingSlot<String>

  @set:Inject
  internal lateinit var gson: Gson

  @Before
  fun setup() {
    hiltRule.inject()
    context = ApplicationProvider.getApplicationContext()
    jsonSlot = slot()
    sound = mockk(relaxed = true) {
      every { src } returns arrayOf("test")
      every { isLooping } returns false
    }

    session = mockk(relaxed = true) {
      every { sendMessage(namespace, capture(jsonSlot)) } returns mockk()
    }

    mockSettingsRepository = mockk(relaxed = true) {
      every { getSoundFadeInDurationMillis() } returns 1000
    }

    playbackStrategy = CastPlaybackStrategy(session, namespace, sound, gson, mockSettingsRepository)
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
