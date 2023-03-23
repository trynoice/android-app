package com.github.ashutoshgngwr.noice.engine

import android.content.Context
import android.media.AudioManager
import android.media.VolumeProvider
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.VolumeProviderCompat
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.shadow.ShadowMediaSessionCompat
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMediaSessionCompat::class])
class SoundPlayerManagerMediaSessionTest {

  private lateinit var session: SoundPlayerManagerMediaSession

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    session = SoundPlayerManagerMediaSession(
      context = context,
      sessionActivityPi = mockk(relaxed = true),
    )
  }

  @Test
  fun setAudioStream_onLocalPlayback() {
    listOf(AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC).forEach { stream ->
      session.setAudioStream(stream)
      assertTrue(ShadowMediaSessionCompat.isLocalPlayback)
      assertEquals(stream, ShadowMediaSessionCompat.currentAudioStream)
    }
  }

  @Test
  fun setAudioStream_setPlaybackToLocal_setPlaybackToRemote() {
    listOf(AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC).forEach { newStream ->
      val remoteVolumeProvider = mockk<VolumeProviderCompat>(relaxed = true) {
        every { volumeProvider } returns mockk<VolumeProvider>(relaxed = true)
      }

      session.setPlaybackToRemote(remoteVolumeProvider)
      assertFalse(ShadowMediaSessionCompat.isLocalPlayback)
      assertEquals(remoteVolumeProvider, ShadowMediaSessionCompat.currentVolumeProvider)

      val oldStream = ShadowMediaSessionCompat.currentAudioStream
      session.setAudioStream(newStream)
      assertEquals(oldStream, ShadowMediaSessionCompat.currentAudioStream)

      session.setPlaybackToLocal()
      assertTrue(ShadowMediaSessionCompat.isLocalPlayback)
      assertEquals(newStream, ShadowMediaSessionCompat.currentAudioStream)
    }
  }

  @Test
  fun setState() {
    mapOf(
      SoundPlayerManager.State.PLAYING to PlaybackStateCompat.STATE_PLAYING,
      SoundPlayerManager.State.PAUSING to PlaybackStateCompat.STATE_PLAYING,
      SoundPlayerManager.State.PAUSED to PlaybackStateCompat.STATE_PAUSED,
      SoundPlayerManager.State.STOPPING to PlaybackStateCompat.STATE_PLAYING,
      SoundPlayerManager.State.STOPPED to PlaybackStateCompat.STATE_STOPPED,
    ).forEach { (managerState, expectedSessionState) ->
      session.setState(managerState)
      val actualSessionState = ShadowMediaSessionCompat.currentPlaybackState?.state
      assertEquals("for manager state $managerState", expectedSessionState, actualSessionState)
    }
  }

  @Test
  fun setCurrentPresetName() {
    mapOf(
      "test-preset-name-1" to "test-preset-name-1",
      "test-preset-name-2" to "test-preset-name-2",
      null to ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.unsaved_preset),
    ).forEach { (presetName, expectedMetadataTitle) ->
      session.setCurrentPresetName(presetName)
      val actualMetadataTitle = ShadowMediaSessionCompat.currentMetadata
        ?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)

      assertEquals("for preset name $presetName", expectedMetadataTitle, actualMetadataTitle)
    }
  }

  @Test
  fun setCallback() {
    val callbackMock = mockk<SoundPlayerManagerMediaSession.Callback>(relaxed = true)
    session.setCallback(callbackMock)

    clearMocks(callbackMock)
    ShadowMediaSessionCompat.currentCallback?.onPlay()
    verify(exactly = 1) { callbackMock.onPlay() }

    clearMocks(callbackMock)
    ShadowMediaSessionCompat.currentCallback?.onStop()
    verify(exactly = 1) { callbackMock.onStop() }

    clearMocks(callbackMock)
    ShadowMediaSessionCompat.currentCallback?.onPause()
    verify(exactly = 1) { callbackMock.onPause() }

    clearMocks(callbackMock)
    ShadowMediaSessionCompat.currentCallback?.onSkipToPrevious()
    verify(exactly = 1) { callbackMock.onSkipToPrevious() }

    clearMocks(callbackMock)
    ShadowMediaSessionCompat.currentCallback?.onSkipToNext()
    verify(exactly = 1) { callbackMock.onSkipToNext() }
  }

  @Test
  fun release() {
    assertFalse(ShadowMediaSessionCompat.isReleased)
    session.release()
    assertEquals(true, ShadowMediaSessionCompat.isReleased)
  }
}
