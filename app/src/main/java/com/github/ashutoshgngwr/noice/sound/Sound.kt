package com.github.ashutoshgngwr.noice.sound

import androidx.annotation.StringRes
import com.github.ashutoshgngwr.noice.R

/**
 *  A data class that holds reference to a sound effect's asset path, title
 *  resource id from android resources and whether or not is it loopable.
 *  It also declares a static instance 'LIBRARY' that defines the complete
 *  sound library supported by Noice.
 */
class Sound private constructor(
  val key: String, @StringRes val titleResId: Int,
  val isLoopable: Boolean
) {

  /**
   * The simplified version of the constructor to initialize looping sounds.
   */
  private constructor(key: String, @StringRes titleResId: Int) : this(key, titleResId, true)

  companion object {
    /**
     * Static sound library with various effects. key is used to lookup the file in assets.
     */
    val LIBRARY = mapOf(
      "birds" to Sound("birds", R.string.birds),
      "bonfire" to Sound("bonfire", R.string.bonfire),
      "coffee_shop" to Sound("coffee_shop", R.string.coffee_shop),
      "distant_thunder" to Sound("distant_thunder", R.string.distant_thunder, false),
      "heavy_rain" to Sound("heavy_rain", R.string.heavy_rain),
      "light_rain" to Sound("light_rain", R.string.light_rain),
      "moderate_rain" to Sound("moderate_rain", R.string.moderate_rain),
      "morning_in_a_village" to Sound("morning_in_a_village", R.string.morning_in_a_village),
      "moving_train" to Sound("moving_train", R.string.moving_train),
      "night" to Sound("night", R.string.night),
      "rolling_thunder" to Sound("rolling_thunder", R.string.rolling_thunder, false),
      "seaside" to Sound("seaside", R.string.seaside),
      "soft_wind" to Sound("soft_wind", R.string.soft_wind),
      "thunder_crack" to Sound("thunder_crack", R.string.thunder_crack, false),
      "train_horn" to Sound("train_horn", R.string.train_horn, false),
      "water_stream" to Sound("water_stream", R.string.water_stream),
      "wind_chimes_of_shells" to Sound("wind_chimes_of_shells", R.string.wind_in_chimes_of_shells),
      "wind_in_palm_trees" to Sound("wind_in_palm_trees", R.string.wind_in_palm_trees)
    )
  }
}
