package com.github.ashutoshgngwr.noice.shadows

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow.invokeConstructor
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector

/**
 * Shadow implementation for [MediaSession].
 */
@Suppress("unused", "TestFunctionName")
@Implements(value = MediaSession::class)
class ShadowMediaSession : Player.Listener {

  @RealObject
  private lateinit var realMediaSession: MediaSession
  private lateinit var player: Player

  var deviceInfo: DeviceInfo? = null; private set
  var deviceVolume: Int = 0; private set
  var isDeviceMute: Boolean = false; private set
  var playerPlaybackState: Int = Player.STATE_IDLE; private set
  var playerPlayWhenReady: Boolean = false; private set
  var playerVolume: Float = 0F; private set
  var playerAudioAttribute: AudioAttributes? = null; private set
  var playerCurrentMediaMetadata: MediaMetadata? = null; private set
  var isReleased: Boolean = false; private set

  @Implementation
  fun __constructor__(
    context: Context,
    id: String,
    player: Player,
    sessionActivity: PendingIntent?,
    customLayout: ImmutableList<CommandButton>,
    callback: MediaSession.Callback,
    tokenExtras: Bundle,
    bitmapLoader: BitmapLoader,
  ) {
    invokeConstructor(
      MediaSession::class.java,
      realMediaSession,
      ReflectionHelpers.ClassParameter.from(Context::class.java, context),
      ReflectionHelpers.ClassParameter.from(String::class.java, id),
      ReflectionHelpers.ClassParameter.from(Player::class.java, player),
      ReflectionHelpers.ClassParameter.from(PendingIntent::class.java, sessionActivity),
      ReflectionHelpers.ClassParameter.from(ImmutableList::class.java, customLayout),
      ReflectionHelpers.ClassParameter.from(MediaSession.Callback::class.java, callback),
      ReflectionHelpers.ClassParameter.from(Bundle::class.java, tokenExtras),
      ReflectionHelpers.ClassParameter.from(BitmapLoader::class.java, bitmapLoader),
    )

    this.player = player
    player.addListener(this)
  }

  @Implementation
  fun release() {
    isReleased = true
    player.removeListener(this)
    reflector(Reflector::class.java, realMediaSession).release()
  }

  fun sendSetDeviceVolumeCommand(volume: Int) {
    player.setDeviceVolume(volume, 0)
  }

  fun sendSetDeviceMuteCommand(isMuted: Boolean) {
    player.setDeviceMuted(isMuted, 0)
  }

  fun sendIncreaseDeviceVolumeCommand() {
    player.increaseDeviceVolume(0)
  }

  fun sendDecreaseDeviceVolumeCommand() {
    player.decreaseDeviceVolume(0)
  }

  fun sendSetPlayWhenReadyCommand(playWhenReady: Boolean) {
    player.playWhenReady = playWhenReady
  }

  fun sendStopCommand() {
    player.stop()
  }

  fun sendSeekToNextMediaItem() {
    player.seekToNextMediaItem()
  }

  fun sendSeekToPreviousMediaItem() {
    player.seekToPreviousMediaItem()
  }

  fun sendSetVolumeCommand(volume: Float) {
    player.volume = volume
  }

  override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
    this.deviceInfo = deviceInfo
  }

  override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
    deviceVolume = volume
    isDeviceMute = muted
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    playerPlaybackState = playbackState
  }

  override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
    playerPlayWhenReady = playWhenReady
  }

  override fun onVolumeChanged(volume: Float) {
    playerVolume = volume
  }

  override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) {
    playerAudioAttribute = audioAttributes
  }

  override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
    playerCurrentMediaMetadata = mediaMetadata
  }

  @ForType(value = MediaSession::class, direct = true)
  private interface Reflector {
    fun release()
  }
}
