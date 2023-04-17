package com.github.ashutoshgngwr.noice.cast

import com.github.ashutoshgngwr.noice.engine.SoundPlayerManagerMediaSession
import com.google.android.gms.cast.framework.CastContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CastVolumeProvider(
  private val context: CastContext,
) : SoundPlayerManagerMediaSession.RemoteDeviceVolumeProvider {

  override fun getMaxVolume(): Int {
    return MAX_VOLUME
  }

  override fun getVolume(): Int {
    return ((context.sessionManager.currentCastSession?.volume ?: 1.0) * MAX_VOLUME).roundToInt()
  }

  override fun setVolume(volume: Int) {
    context.sessionManager.currentCastSession?.volume = volume.toDouble() / MAX_VOLUME
  }

  override fun increaseVolume() {
    setVolume(min(MAX_VOLUME, getVolume() + 1))
  }

  override fun decreaseVolume() {
    setVolume(max(0, getVolume() - 1))
  }

  override fun isMuted(): Boolean {
    return context.sessionManager.currentCastSession?.isMute ?: false
  }

  override fun setMuted(muted: Boolean) {
    context.sessionManager.currentCastSession?.isMute = muted
  }

  companion object {
    private const val MAX_VOLUME = 20
  }
}
