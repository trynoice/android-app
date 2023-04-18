package com.github.ashutoshgngwr.noice.engine

import androidx.media3.common.AudioAttributes
import com.github.ashutoshgngwr.noice.TestDispatcherRule
import com.github.ashutoshgngwr.noice.engine.media.FakeMediaPlayer
import com.github.ashutoshgngwr.noice.engine.media.MediaPlayer
import com.github.ashutoshgngwr.noice.models.Sound
import com.github.ashutoshgngwr.noice.models.SoundInfo
import com.github.ashutoshgngwr.noice.models.SoundSegment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class LocalSoundPlayerTest {

  @get:Rule
  val testDispatcherRule = TestDispatcherRule()

  private val contiguousSound = Sound(
    info = SoundInfo(
      id = "test-sound-id-1",
      group = mockk(),
      name = "test-sound-name-1",
      iconSvg = "",
      maxSilence = 0,
      isPremium = false,
      hasPremiumSegments = true,
      tags = listOf(),
      sources = listOf(),
    ),
    segments = listOf(
      SoundSegment(
        name = "test-segment-1",
        basePath = "test-path-1",
        isFree = true,
        isBridgeSegment = false,
      ),
      SoundSegment(
        name = "test-segment-2",
        basePath = "test-path-2",
        isFree = false,
        isBridgeSegment = false,
      ),
      SoundSegment(
        name = "test-bridge-1",
        basePath = "test-bridge-path-1",
        isFree = true,
        isBridgeSegment = true,
        from = "test-segment-1",
        to = "test-segment-1",
      ),
      SoundSegment(
        name = "test-bridge-2",
        basePath = "test-bridge-path-2",
        isFree = false,
        isBridgeSegment = true,
        from = "test-segment-1",
        to = "test-segment-2",
      ),
      SoundSegment(
        name = "test-bridge-3",
        basePath = "test-bridge-path-3",
        isFree = false,
        isBridgeSegment = true,
        from = "test-segment-2",
        to = "test-segment-2",
      ),
      SoundSegment(
        name = "test-bridge-4",
        basePath = "test-bridge-path-4",
        isFree = false,
        isBridgeSegment = true,
        from = "test-segment-2",
        to = "test-segment-1",
      ),
    )
  )

  private val nonContiguousSound = Sound(
    info = SoundInfo(
      id = "test-sound-id-2",
      group = mockk(),
      name = "test-sound-name-2",
      iconSvg = "",
      maxSilence = 60,
      isPremium = false,
      hasPremiumSegments = true,
      tags = listOf(),
      sources = listOf(),
    ),
    segments = listOf(
      SoundSegment(
        name = "test-segment-1",
        basePath = "test-path-1",
        isFree = true,
        isBridgeSegment = false,
      ),
      SoundSegment(
        name = "test-segment-2",
        basePath = "test-path-2",
        isFree = false,
        isBridgeSegment = false,
      ),
    )
  )

  private lateinit var metadataSourceMock: LocalSoundPlayer.SoundMetadataSource

  @Before
  fun setUp() {
    metadataSourceMock = mockk {
      coEvery { load() } returns contiguousSound
    }
  }

  @Test
  fun initialState() = runTest {
    val soundPlayer = LocalSoundPlayer(metadataSourceMock, FakeMediaPlayer(), this)
    assertEquals(SoundPlayer.State.PAUSED, soundPlayer.state)
  }

  @Test
  fun setFadeInDuration() = runTest {
    val fakeMediaPlayer = FakeMediaPlayer()
    val fadeInDuration = 28.seconds
    val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
    soundPlayer.setFadeInDuration(fadeInDuration)
    advanceUntilIdle()

    assertNull(fakeMediaPlayer.pendingFadeTransition)
    soundPlayer.play()
    fakeMediaPlayer.setStateTo(MediaPlayer.State.PLAYING)
    assertEquals(fadeInDuration, fakeMediaPlayer.pendingFadeTransition?.duration)
  }

  @Test
  fun setFadeOutDuration() = runTest {
    val fakeMediaPlayer = FakeMediaPlayer()
    val fadeOutDuration = 32.seconds
    val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
    soundPlayer.setFadeOutDuration(fadeOutDuration)
    advanceUntilIdle()

    soundPlayer.play()
    fakeMediaPlayer.setStateTo(MediaPlayer.State.PLAYING)
    fakeMediaPlayer.consumePendingFadeTransition()
    assertNull(fakeMediaPlayer.pendingFadeTransition)
    soundPlayer.pause(false)
    assertEquals(fadeOutDuration, fakeMediaPlayer.pendingFadeTransition?.duration)
  }

  @Test
  fun setPremiumSegmentsEnabled_setAudioBitrate() = runTest {
    data class TestCase(
      val isPremiumSegmentsEnabled: Boolean,
      val bitrate: String,
      val expectedPlaylistUriSuffixes: List<String>
    )

    listOf(
      TestCase(
        isPremiumSegmentsEnabled = false,
        bitrate = "128k",
        expectedPlaylistUriSuffixes = contiguousSound.segments
          .filter { it.isFree }
          .map { it.path("128k") },
      ),
      TestCase(
        isPremiumSegmentsEnabled = true,
        bitrate = "128k",
        expectedPlaylistUriSuffixes = contiguousSound.segments
          .map { it.path("128k") },
      ),
      TestCase(
        isPremiumSegmentsEnabled = false,
        bitrate = "256k",
        expectedPlaylistUriSuffixes = contiguousSound.segments
          .filter { it.isFree }
          .map { it.path("256k") },
      ),
      TestCase(
        isPremiumSegmentsEnabled = true,
        bitrate = "320k",
        expectedPlaylistUriSuffixes = contiguousSound.segments
          .map { it.path("320k") },
      ),
    ).forEach { testCase ->
      val fakeMediaPlayer = FakeMediaPlayer()
      val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
      advanceUntilIdle()
      soundPlayer.play()
      assertNotNull(fakeMediaPlayer.nextPlaylistItem())

      soundPlayer.setPremiumSegmentsEnabled(testCase.isPremiumSegmentsEnabled)
      soundPlayer.setAudioBitrate(testCase.bitrate)

      repeat(10) {// ensure that random selection actually picks a premium segment.
        val queuedUri = fakeMediaPlayer.nextPlaylistItem()
        assertNotNull(queuedUri)
        assertTrue(testCase.expectedPlaylistUriSuffixes.any { queuedUri?.endsWith(it) ?: false })
        fakeMediaPlayer.consumeNextPlaylistItem()
      }
    }
  }

  @Test
  fun setAudioAttributes() = runTest {
    val fakeMediaPlayer = FakeMediaPlayer()
    val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
    val attrs = mockk<AudioAttributes>()
    soundPlayer.setAudioAttributes(attrs)
    assertEquals(attrs, fakeMediaPlayer.audioAttributes)
  }

  @Test
  fun setVolume() = runTest {
    val fakeMediaPlayer = FakeMediaPlayer()
    val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
    advanceUntilIdle()
    soundPlayer.setVolume(0F)
    assertNull(fakeMediaPlayer.pendingFadeTransition)
    assertEquals(0F, fakeMediaPlayer.volume)

    soundPlayer.play()
    fakeMediaPlayer.setStateTo(MediaPlayer.State.PLAYING)
    soundPlayer.setVolume(1F)
    assertEquals(1F, fakeMediaPlayer.pendingFadeTransition?.toVolume)
  }

  @Test
  fun play() = runTest {
    listOf(contiguousSound, nonContiguousSound).forEach { sound ->
      coEvery {
        metadataSourceMock.load()
      } throws LocalSoundPlayer.SoundMetadataSource.LoadException("test-error", null) andThen sound

      val fakeMediaPlayer = FakeMediaPlayer()
      val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
      soundPlayer.play()
      assertEquals(SoundPlayer.State.BUFFERING, soundPlayer.state)
      assertNull(fakeMediaPlayer.nextPlaylistItem())

      advanceUntilIdle()
      coVerify(atLeast = 2) { metadataSourceMock.load() }

      assertNotNull(fakeMediaPlayer.nextPlaylistItem())
      assertNull(fakeMediaPlayer.pendingFadeTransition)
      assertEquals(SoundPlayer.State.BUFFERING, soundPlayer.state)

      fakeMediaPlayer.setStateTo(MediaPlayer.State.PLAYING)
      assertEquals(SoundPlayer.State.PLAYING, soundPlayer.state)
      assertEquals(0F, fakeMediaPlayer.pendingFadeTransition?.fromVolume)
      assertEquals(1F, fakeMediaPlayer.pendingFadeTransition?.toVolume)
      fakeMediaPlayer.consumePendingFadeTransition()

      repeat(10) {
        fakeMediaPlayer.consumeNextPlaylistItem()
        if (sound.info.maxSilence > 0) {
          assertNull(fakeMediaPlayer.nextPlaylistItem())
          assertEquals(SoundPlayer.State.PLAYING, soundPlayer.state)
          advanceTimeBy(sound.info.maxSilence * 1000L)
        }

        assertNotNull(fakeMediaPlayer.nextPlaylistItem())
      }
    }
  }

  @Test
  fun pause() = runTest {
    data class TestCase(
      val mediaPlayerState: MediaPlayer.State,
      val pauseImmediate: Boolean,
      val isExpectingFadeTransition: Boolean,
    )

    listOf(
      TestCase(
        mediaPlayerState = MediaPlayer.State.BUFFERING,
        pauseImmediate = false,
        isExpectingFadeTransition = false,
      ),
      TestCase(
        mediaPlayerState = MediaPlayer.State.BUFFERING,
        pauseImmediate = true,
        isExpectingFadeTransition = false,
      ),
      TestCase(
        mediaPlayerState = MediaPlayer.State.PLAYING,
        pauseImmediate = false,
        isExpectingFadeTransition = true,
      ),
      TestCase(
        mediaPlayerState = MediaPlayer.State.PLAYING,
        pauseImmediate = true,
        isExpectingFadeTransition = false,
      ),
    ).forEach { testCase ->
      val fakeMediaPlayer = FakeMediaPlayer()
      val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
      advanceUntilIdle()
      soundPlayer.play()
      fakeMediaPlayer.setStateTo(testCase.mediaPlayerState)
      fakeMediaPlayer.consumePendingFadeTransition()

      assertNull(fakeMediaPlayer.pendingFadeTransition)
      soundPlayer.pause(testCase.pauseImmediate)
      if (testCase.isExpectingFadeTransition) {
        assertEquals(SoundPlayer.State.PAUSING, soundPlayer.state)
        assertEquals(1F, fakeMediaPlayer.pendingFadeTransition?.fromVolume)
        assertEquals(0F, fakeMediaPlayer.pendingFadeTransition?.toVolume)
        fakeMediaPlayer.consumePendingFadeTransition()
      }

      assertEquals(SoundPlayer.State.PAUSED, soundPlayer.state)
    }
  }

  @Test
  fun stop() = runTest {
    data class TestCase(
      val mediaPlayerState: MediaPlayer.State,
      val stopImmediate: Boolean,
      val isExpectingFadeTransition: Boolean,
    )

    listOf(
      TestCase(
        mediaPlayerState = MediaPlayer.State.BUFFERING,
        stopImmediate = false,
        isExpectingFadeTransition = false,
      ),
      TestCase(
        mediaPlayerState = MediaPlayer.State.BUFFERING,
        stopImmediate = true,
        isExpectingFadeTransition = false,
      ),
      TestCase(
        mediaPlayerState = MediaPlayer.State.PLAYING,
        stopImmediate = false,
        isExpectingFadeTransition = true,
      ),
      TestCase(
        mediaPlayerState = MediaPlayer.State.PLAYING,
        stopImmediate = true,
        isExpectingFadeTransition = false,
      ),
    ).forEach { testCase ->
      val fakeMediaPlayer = FakeMediaPlayer()
      val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
      advanceUntilIdle()
      soundPlayer.play()
      fakeMediaPlayer.setStateTo(testCase.mediaPlayerState)
      fakeMediaPlayer.consumePendingFadeTransition()

      assertNull(fakeMediaPlayer.pendingFadeTransition)
      soundPlayer.stop(testCase.stopImmediate)
      if (testCase.isExpectingFadeTransition) {
        assertEquals(SoundPlayer.State.STOPPING, soundPlayer.state)
        assertEquals(1F, fakeMediaPlayer.pendingFadeTransition?.fromVolume)
        assertEquals(0F, fakeMediaPlayer.pendingFadeTransition?.toVolume)
        fakeMediaPlayer.consumePendingFadeTransition()
      }

      assertEquals(SoundPlayer.State.STOPPED, soundPlayer.state)
    }
  }

  @Test
  fun setListener() = runTest {
    val listenerMock = mockk<SoundPlayer.StateChangeListener>(relaxed = true)
    val fakeMediaPlayer = FakeMediaPlayer()
    val soundPlayer = LocalSoundPlayer(metadataSourceMock, fakeMediaPlayer, this)
    soundPlayer.setStateChangeListener(listenerMock)

    advanceUntilIdle()
    soundPlayer.play()
    verify(exactly = 1) { listenerMock.onSoundPlayerStateChanged(SoundPlayer.State.BUFFERING) }

    fakeMediaPlayer.setStateTo(MediaPlayer.State.PLAYING)
    verify(exactly = 1) { listenerMock.onSoundPlayerStateChanged(SoundPlayer.State.PLAYING) }

    soundPlayer.pause(true)
    verify(exactly = 1) { listenerMock.onSoundPlayerStateChanged(SoundPlayer.State.PAUSED) }
  }
}
