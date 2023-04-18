package com.github.ashutoshgngwr.noice.cast

import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.engine.SoundPlaybackMediaSession
import com.google.android.gms.cast.framework.CastContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A [remote volume provider][SoundPlaybackMediaSession.RemoteDeviceVolumeProvider] for managing
 * volume of the connected Google Cast device.
 */
class CastVolumeProvider(
  private val context: CastContext,
) : SoundPlaybackMediaSession.RemoteDeviceVolumeProvider {

  private var isMute: Boolean = context.sessionManager.currentCastSession?.isMute ?: false
  private var currentVolume: Int =
    ((context.sessionManager.currentCastSession?.volume ?: 1.0) * MAX_VOLUME).roundToInt()

  override fun getMaxVolume(): Int {
    return MAX_VOLUME
  }

  override fun getVolume(): Int {
    return currentVolume
  }

  override fun setVolume(volume: Int) {
    currentVolume = volume
    context.sessionManager.currentCastSession?.volume = volume.toDouble() / MAX_VOLUME
  }

  override fun increaseVolume() {
    setVolume(min(MAX_VOLUME, getVolume() + 1))
  }

  override fun decreaseVolume() {
    setVolume(max(0, getVolume() - 1))
  }

  override fun isMute(): Boolean {
    return isMute
  }

  override fun setMute(enabled: Boolean) {
    isMute = enabled
    context.sessionManager.currentCastSession?.isMute = enabled
  }

  companion object {
    @VisibleForTesting
    const val MAX_VOLUME = 20
  }
}
