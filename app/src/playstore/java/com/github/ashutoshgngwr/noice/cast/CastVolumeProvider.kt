package com.github.ashutoshgngwr.noice.cast

import androidx.media.VolumeProviderCompat
import com.google.android.gms.cast.framework.CastSession
import kotlin.math.round

/**
 * A [VolumeProviderCompat] implementation for adjusting cast device volume using active
 * [MediaSession][android.support.v4.media.session.MediaSessionCompat]'s remote playback
 * control.
 */
class CastVolumeProvider(private val session: CastSession) :
  VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, MAX_VOLUME, multiply(session.volume)) {

  companion object {
    private const val MAX_VOLUME = 15
    private fun multiply(volume: Double): Int {
      return round(volume * MAX_VOLUME).toInt()
    }
  }

  override fun onSetVolumeTo(volume: Int) {
    session.volume = volume.toDouble() / MAX_VOLUME
    this.currentVolume = volume
  }

  override fun onAdjustVolume(direction: Int) {
    onSetVolumeTo(this.currentVolume + direction)
  }
}
