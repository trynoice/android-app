package com.github.ashutoshgngwr.noice.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.shadows.ShadowMediaSession
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMediaSession::class])
class SoundPlaybackMediaSessionTest {

  private lateinit var session: SoundPlaybackMediaSession
  private lateinit var mediaSessionShadow: ShadowMediaSession

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    session = SoundPlaybackMediaSession(
      context = context,
      sessionActivityPi = mockk(relaxed = true),
    )
    mediaSessionShadow = Shadow.extract(session.session)
  }

  @After
  fun tearDown() {
    session.release()
  }

  @Test
  fun setPlaybackToRemote_setPlaybackToLocal() {
    val fakeVolumeProvider = FakeVolumeProvider(maxVolume = 20, volume = 10, isMute = true)
    session.setPlaybackToRemote(fakeVolumeProvider)

    assertEquals(DeviceInfo.PLAYBACK_TYPE_REMOTE, mediaSessionShadow.deviceInfo?.playbackType)
    assertEquals(fakeVolumeProvider.getMaxVolume(), mediaSessionShadow.deviceInfo?.maxVolume)
    assertEquals(fakeVolumeProvider.getVolume(), mediaSessionShadow.deviceVolume)
    assertEquals(fakeVolumeProvider.isMute(), mediaSessionShadow.isDeviceMute)

    mediaSessionShadow.sendSetDeviceVolumeCommand(15)
    mediaSessionShadow.sendSetDeviceMuteCommand(false)
    assertEquals(15, fakeVolumeProvider.getVolume())
    assertEquals(false, fakeVolumeProvider.isMute())
    // also check if changes propagate back to the media session.
    assertEquals(15, mediaSessionShadow.deviceVolume)
    assertEquals(false, mediaSessionShadow.isDeviceMute)

    mediaSessionShadow.sendIncreaseDeviceVolumeCommand()
    assertEquals(16, fakeVolumeProvider.getVolume())

    mediaSessionShadow.sendDecreaseDeviceVolumeCommand()
    assertEquals(15, fakeVolumeProvider.getVolume())

    session.setPlaybackToLocal()
    assertEquals(DeviceInfo.PLAYBACK_TYPE_LOCAL, mediaSessionShadow.deviceInfo?.playbackType)

    mediaSessionShadow.sendSetDeviceVolumeCommand(5)
    assertNotEquals(5, fakeVolumeProvider.getVolume())
  }

  @Test
  fun setState() {
    listOf(
      Triple(SoundPlayerManager.State.PLAYING, Player.STATE_READY, true),
      Triple(SoundPlayerManager.State.PAUSING, Player.STATE_READY, true),
      Triple(SoundPlayerManager.State.PAUSED, Player.STATE_READY, false),
      Triple(SoundPlayerManager.State.STOPPING, Player.STATE_READY, true),
      Triple(SoundPlayerManager.State.STOPPED, Player.STATE_ENDED, false),
    ).forEach { (managerState, expectedPlayerPlaybackState, expectedPlayerPlayWhenReady) ->
      session.setState(managerState)
      assertEquals(expectedPlayerPlaybackState, mediaSessionShadow.playerPlaybackState)
      assertEquals(expectedPlayerPlayWhenReady, mediaSessionShadow.playerPlayWhenReady)
    }
  }

  @Test
  fun setVolume() {
    listOf(0.2F, 0.4F, 0.6F, 0.8F, 1F).forEach { volume ->
      session.setVolume(volume)
      assertEquals(volume, mediaSessionShadow.playerVolume)
    }
  }

  @Test
  fun setAudioAttributes() {
    listOf(
      AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
        .build(),
      AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
        .setUsage(C.USAGE_ALARM)
        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_SYSTEM)
        .build()
    ).forEach { attributes ->
      session.setAudioAttributes(attributes)
      assertEquals(attributes, mediaSessionShadow.playerAudioAttribute)
    }
  }

  @Test
  fun setPresetName() {
    mapOf(
      "test-preset-name-1" to "test-preset-name-1",
      "test-preset-name-2" to "test-preset-name-2",
      null to ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.unsaved_preset),
    ).forEach { (presetName, expectedMetadataTitle) ->
      session.setPresetName(presetName)
      assertEquals(expectedMetadataTitle, mediaSessionShadow.playerCurrentMediaMetadata?.title)
    }
  }

  @Test
  fun setCallback() {
    val callbackMock = mockk<SoundPlaybackMediaSession.Callback>(relaxed = true)
    session.setCallback(callbackMock)

    mediaSessionShadow.sendSetPlayWhenReadyCommand(true)
    verify(exactly = 1) { callbackMock.onPlay() }

    mediaSessionShadow.sendSetPlayWhenReadyCommand(false)
    verify(exactly = 1) { callbackMock.onPause() }

    mediaSessionShadow.sendStopCommand()
    verify(exactly = 1) { callbackMock.onStop() }

    mediaSessionShadow.sendSeekToPreviousMediaItem()
    verify(exactly = 0) { callbackMock.onSkipToPrevious() }

    mediaSessionShadow.sendSeekToNextMediaItem()
    verify(exactly = 0) { callbackMock.onSkipToNext() }

    session.setPresetName("saved-preset")
    mediaSessionShadow.sendSeekToPreviousMediaItem()
    verify(exactly = 1) { callbackMock.onSkipToPrevious() }

    mediaSessionShadow.sendSeekToNextMediaItem()
    verify(exactly = 1) { callbackMock.onSkipToNext() }

    mediaSessionShadow.sendSetVolumeCommand(0.5F)
    verify(exactly = 1) { callbackMock.onSetVolume(0.5F) }
  }

  @Test
  fun release() {
    assertFalse(mediaSessionShadow.isReleased)
    session.release()
    assertTrue(mediaSessionShadow.isReleased)
  }

  class FakeVolumeProvider(
    private val maxVolume: Int,
    private var volume: Int,
    private var isMute: Boolean,
  ) : SoundPlaybackMediaSession.RemoteDeviceVolumeProvider {

    override fun getMaxVolume(): Int {
      return maxVolume
    }

    override fun getVolume(): Int {
      return volume
    }

    override fun setVolume(volume: Int) {
      this.volume = volume
    }

    override fun increaseVolume() {
      this.volume += 1
    }

    override fun decreaseVolume() {
      this.volume -= 1
    }

    override fun isMute(): Boolean {
      return isMute
    }

    override fun setMute(enabled: Boolean) {
      isMute = enabled
    }
  }
}
