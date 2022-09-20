package com.github.ashutoshgngwr.noice.cast

import androidx.media.VolumeProviderCompat
import com.google.android.gms.cast.framework.CastContext
import kotlin.math.round

/**
 * A [VolumeProviderCompat] implementation for adjusting cast device volume using active
 * [MediaSession][android.support.v4.media.session.MediaSessionCompat]'s remote playback
 * control.
 */
class CastVolumeProvider(
  private val castContext: CastContext,
) : VolumeProviderCompat(
  VOLUME_CONTROL_ABSOLUTE,
  MAX_VOLUME,
  multiply(castContext.sessionManager.currentCastSession?.volume ?: 0.0)
) {

  override fun onSetVolumeTo(volume: Int) {
    val session = castContext.sessionManager.currentCastSession ?: return
    session.volume = volume.toDouble() / MAX_VOLUME
    this.currentVolume = volume
  }

  override fun onAdjustVolume(direction: Int) {
    onSetVolumeTo(this.currentVolume + direction)
  }

  companion object {
    private const val MAX_VOLUME = 15

    private fun multiply(volume: Double): Int {
      return round(volume * MAX_VOLUME).toInt()
    }
  }
}
