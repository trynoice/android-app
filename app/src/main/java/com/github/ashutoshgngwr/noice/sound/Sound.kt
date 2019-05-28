package com.github.ashutoshgngwr.noice.sound

import android.util.SparseArray
import androidx.core.util.set
import com.github.ashutoshgngwr.noice.R

class Sound private constructor(val resId: Int, val titleResId: Int) {

  companion object {
    val SOUND_LIBRARY: SparseArray<Sound> = SparseArray()

    init {
      SOUND_LIBRARY[R.raw.leaves_1] = Sound(R.raw.leaves_1, R.string.sound_leaves_1)
      SOUND_LIBRARY[R.raw.leaves_2] = Sound(R.raw.leaves_2, R.string.sound_leaves_2)
      SOUND_LIBRARY[R.raw.rain_1] = Sound(R.raw.rain_1, R.string.sound_rain_1)
      SOUND_LIBRARY[R.raw.rain_2] = Sound(R.raw.rain_2, R.string.sound_rain_2)
      SOUND_LIBRARY[R.raw.rain_3] = Sound(R.raw.rain_3, R.string.sound_rain_3)
      SOUND_LIBRARY[R.raw.thunder_1] = Sound(R.raw.thunder_1, R.string.sound_thunder_1, false)
      SOUND_LIBRARY[R.raw.thunder_2] = Sound(R.raw.thunder_2, R.string.sound_thunder_2, false)
      SOUND_LIBRARY[R.raw.thunder_3] = Sound(R.raw.thunder_3, R.string.sound_thunder_3, false)
      SOUND_LIBRARY[R.raw.wind_1] = Sound(R.raw.wind_1, R.string.sound_wind_1)
      SOUND_LIBRARY[R.raw.wind_2] = Sound(R.raw.wind_2, R.string.sound_wind_2)
    }
  }

  var streamId = 0
  var isLoopable = true

  private constructor(resId: Int, titleResId: Int, isLoopable: Boolean) : this(resId, titleResId) {
    this.isLoopable = isLoopable
  }
}

