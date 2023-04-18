package com.github.ashutoshgngwr.noice.engine

import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.test.core.app.ApplicationProvider
import io.mockk.called
import io.mockk.clearMocks
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioFocusManagerTest {

  private lateinit var listenerMock: AudioFocusManager.Listener
  private lateinit var focusManager: AudioFocusManager

  private val audioAttributes = AudioAttributes.Builder()
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .setUsage(C.USAGE_ALARM)
    .build()

  @Before
  fun setUp() {
    mockkStatic(AudioManagerCompat::class)
    listenerMock = mockk(relaxed = true)
    focusManager = AudioFocusManager(ApplicationProvider.getApplicationContext())
    focusManager.setAudioAttributes(audioAttributes)
    focusManager.setListener(listenerMock)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun requestAudioFocus_onRequestGranted() {
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    focusManager.requestFocus()
    assertEquals(true, focusManager.hasFocus)
    verify(exactly = 1) { listenerMock.onAudioFocusGained() }
    verify(exactly = 1) {
      AudioManagerCompat.requestAudioFocus(any(), withArg { request ->
        assertEquals(AudioAttributesCompat.USAGE_ALARM, request.audioAttributesCompat.usage)
      })
    }
  }

  @Test
  fun requestAudioFocus_onRequestDelayed() {
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_DELAYED

    focusManager.requestFocus()
    assertEquals(false, focusManager.hasFocus)
    verify { listenerMock wasNot called }

    val focusRequestSlot = slot<AudioFocusRequestCompat>()
    verify(exactly = 1) { AudioManagerCompat.requestAudioFocus(any(), capture(focusRequestSlot)) }
    assertTrue(focusRequestSlot.isCaptured)
    focusRequestSlot.captured
      .onAudioFocusChangeListener
      .onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

    assertEquals(true, focusManager.hasFocus)
    verify(exactly = 1) { listenerMock.onAudioFocusGained() }
  }

  @Test
  fun requestAudioFocus_onRequestFailed() {
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

    focusManager.requestFocus()
    assertEquals(false, focusManager.hasFocus)
    verify { listenerMock wasNot called }
  }

  @Test
  fun requestAudioFocus_onDisabled() {
    focusManager.setDisabled(true)
    focusManager.requestFocus()
    assertEquals(true, focusManager.hasFocus)
    verify(exactly = 0) { AudioManagerCompat.requestAudioFocus(any(), any()) }
  }

  @Test
  fun abandonFocus_onFocusRequestGranted() {
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    every {
      AudioManagerCompat.abandonAudioFocusRequest(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    focusManager.requestFocus()
    assertTrue(focusManager.hasFocus)

    clearMocks(listenerMock)
    focusManager.abandonFocus()
    assertEquals(false, focusManager.hasFocus)
    verify { listenerMock wasNot called }
  }

  @Test
  fun abandonFocus_onFocusRequestFailed() {
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    every {
      AudioManagerCompat.abandonAudioFocusRequest(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

    focusManager.requestFocus()
    assertTrue(focusManager.hasFocus)

    clearMocks(listenerMock)
    focusManager.abandonFocus()
    assertEquals(true, focusManager.hasFocus)
    verify { listenerMock wasNot called }
  }

  @Test
  fun abandonFocus_onDisabled() {
    focusManager.setDisabled(true)
    focusManager.requestFocus()
    assertTrue(focusManager.hasFocus)

    clearMocks(listenerMock)
    focusManager.abandonFocus()
    assertEquals(false, focusManager.hasFocus)
    verify(exactly = 0) { AudioManagerCompat.requestAudioFocus(any(), any()) }
  }

  @Test
  fun focusRequestListener() {
    val focusRequestSlot = slot<AudioFocusRequestCompat>()
    every {
      AudioManagerCompat.requestAudioFocus(any(), capture(focusRequestSlot))
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    focusManager.requestFocus()
    assertTrue(focusManager.hasFocus)
    verify(exactly = 1) { listenerMock.onAudioFocusGained() }

    clearMocks(listenerMock)
    assertTrue(focusRequestSlot.isCaptured)
    val focusChangeListener = focusRequestSlot.captured.onAudioFocusChangeListener

    // since the manager already has focus, it shouldn't invoke its listener on second call.
    focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    verify { listenerMock wasNot called }

    focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
    verify(exactly = 1) { listenerMock.onAudioFocusLost(true) }

    focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    verify(exactly = 1) { listenerMock.onAudioFocusGained() }

    focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
    verify(exactly = 1) { listenerMock.onAudioFocusLost(false) }
  }

  @Test
  fun setAudioAttributes() {
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    every {
      AudioManagerCompat.abandonAudioFocusRequest(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    focusManager.requestFocus()
    assertTrue(focusManager.hasFocus)
    verify(exactly = 1) {
      AudioManagerCompat.requestAudioFocus(any(), withArg { request ->
        assertEquals(AudioAttributesCompat.USAGE_ALARM, request.audioAttributesCompat.usage)
      })
    }

    val newAudioAttributes = AudioAttributes.Builder()
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .setUsage(C.USAGE_MEDIA)
      .build()

    focusManager.setAudioAttributes(newAudioAttributes)
    assertEquals(true, focusManager.hasFocus)

    verify(exactly = 1) {
      AudioManagerCompat.abandonAudioFocusRequest(any(), withArg { request ->
        assertEquals(AudioAttributesCompat.USAGE_ALARM, request.audioAttributesCompat.usage)
      })

      AudioManagerCompat.requestAudioFocus(any(), withArg { request ->
        assertEquals(AudioAttributesCompat.USAGE_MEDIA, request.audioAttributesCompat.usage)
      })
    }
  }

  @Test
  fun setDisabled() {
    every {
      AudioManagerCompat.requestAudioFocus(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    every {
      AudioManagerCompat.abandonAudioFocusRequest(any(), any())
    } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    // shouldn't request focus on enabling and disabling if it didn't already had focus.
    focusManager.setDisabled(true)
    assertEquals(false, focusManager.hasFocus)
    verify(exactly = 0) { AudioManagerCompat.requestAudioFocus(any(), any()) }

    focusManager.setDisabled(false)
    assertEquals(false, focusManager.hasFocus)
    verify(exactly = 0) { AudioManagerCompat.requestAudioFocus(any(), any()) }

    focusManager.requestFocus()
    assertTrue(focusManager.hasFocus)
    clearStaticMockk(AudioManagerCompat::class, answers = false)

    focusManager.setDisabled(true)
    assertEquals(true, focusManager.hasFocus)
    verify(exactly = 1) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
    verify(exactly = 0) { AudioManagerCompat.requestAudioFocus(any(), any()) }

    clearStaticMockk(AudioManagerCompat::class, answers = false)
    focusManager.setDisabled(false)
    assertEquals(true, focusManager.hasFocus)
    verify(exactly = 0) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
    verify(exactly = 1) { AudioManagerCompat.requestAudioFocus(any(), any()) }
  }
}
