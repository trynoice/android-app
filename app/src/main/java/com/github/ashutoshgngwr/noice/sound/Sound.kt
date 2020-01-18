package com.github.ashutoshgngwr.noice.sound

import androidx.annotation.StringRes
import com.github.ashutoshgngwr.noice.R

/**
 *  A data class that holds reference to a sound effect's asset path, title
 *  resource id from android resources and whether or not is it loopable.
 *  It also declares a static instance 'LIBRARY' that defines the complete
 *  sound library supported by Noice.
 */
final class Sound private constructor(
  val key: String, @StringRes val titleResId: Int,
  val isLoopable: Boolean
) {

  private constructor(key: String, @StringRes titleResId: Int) : this(key, titleResId, true)

  companion object {
    val LIBRARY = arrayOf(
      Sound("birds", R.string.birds),
      Sound("bonfire", R.string.bonfire),
      Sound("coffee_shop", R.string.coffee_shop),
      Sound("distant_thunder", R.string.distant_thunder, false),
      Sound("heavy_rain", R.string.heavy_rain),
      Sound("light_rain", R.string.light_rain),
      Sound("moderate_rain", R.string.moderate_rain),
      Sound("morning_in_a_village", R.string.morning_in_a_village),
      Sound("moving_train", R.string.moving_train),
      Sound("night", R.string.night),
      Sound("rolling_thunder", R.string.rolling_thunder, false),
      Sound("seaside", R.string.seaside),
      Sound("soft_wind", R.string.soft_wind),
      Sound("thunder_crack", R.string.thunder_crack, false),
      Sound("train_horn", R.string.train_horn, false),
      Sound("water_stream", R.string.water_stream),
      Sound("wind_chimes_of_shells", R.string.wind_in_chimes_of_shells),
      Sound("wind_in_palm_trees", R.string.wind_in_palm_trees)
    )
  }
}
