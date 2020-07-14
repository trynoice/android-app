package com.github.ashutoshgngwr.noice.sound

import androidx.annotation.StringRes
import com.github.ashutoshgngwr.noice.R

/**
 *  A data class that holds reference to a sound effect's asset path, title
 *  resource id from android resources and whether or not is it looping.
 *  It also declares a static instance 'LIBRARY' that defines the complete
 *  sound library supported by Noice.
 */
class Sound private constructor(
  val key: String,
  @StringRes val titleResId: Int,
  @StringRes val displayGroupResID: Int,
  val isLooping: Boolean
) {

  /**
   * The simplified version of the constructor to initialize looping sounds.
   */
  private constructor(
    key: String,
    @StringRes titleResId: Int,
    @StringRes displayGroupResId: Int
  ) : this(key, titleResId, displayGroupResId, true)

  companion object {
    /**
     * Static sound library with various effects. key is used to lookup the file in assets.
     */
    val LIBRARY = mapOf(
      "airplane_inflight" to Sound(
        "airplane_inflight",
        R.string.airplane_inflight,
        R.string.sound_group__airplane
      ),
      "airplane_seatbelt_beeps" to Sound(
        "airplane_seatbelt_beeps",
        R.string.airplane_seatbelt_beeps, R.string.sound_group__airplane,
        false
      ),
      "birds" to Sound("birds", R.string.birds, R.string.sound_group__jungle),
      "bonfire" to Sound("bonfire", R.string.bonfire, R.string.sound_group__jungle),
      "brownian_noise" to Sound(
        "brownian_noise",
        R.string.brownian_noise,
        R.string.sound_group__raw_noise
      ),
      "coffee_shop" to Sound(
        "coffee_shop",
        R.string.coffee_shop,
        R.string.sound_group__public_gatherings
      ),
      "distant_thunder" to Sound(
        "distant_thunder",
        R.string.distant_thunder,
        R.string.sound_group__monsoon,
        false
      ),
      "heavy_rain" to Sound("heavy_rain", R.string.heavy_rain, R.string.sound_group__monsoon),
      "light_rain" to Sound("light_rain", R.string.light_rain, R.string.sound_group__monsoon),
      "moderate_rain" to Sound(
        "moderate_rain",
        R.string.moderate_rain,
        R.string.sound_group__monsoon
      ),
      "morning_in_a_village" to Sound(
        "morning_in_a_village",
        R.string.morning_in_a_village,
        R.string.sound_group__times_of_day
      ),
      "moving_train" to Sound("moving_train", R.string.moving_train, R.string.sound_group__train),
      "night" to Sound("night", R.string.night, R.string.sound_group__times_of_day),
      "office" to Sound("office", R.string.office, R.string.sound_group__public_gatherings),
      "pink_noise" to Sound("pink_noise", R.string.pink_noise, R.string.sound_group__raw_noise),
      "rolling_thunder" to Sound(
        "rolling_thunder",
        R.string.rolling_thunder,
        R.string.sound_group__monsoon,
        false
      ),
      "seaside" to Sound("seaside", R.string.seaside, R.string.sound_group__waterfront),
      "soft_wind" to Sound("soft_wind", R.string.soft_wind, R.string.sound_group__wind),
      "thunder_crack" to Sound(
        "thunder_crack",
        R.string.thunder_crack,
        R.string.sound_group__monsoon,
        false
      ),
      "train_horn" to Sound("train_horn", R.string.train_horn, R.string.sound_group__train, false),
      "water_stream" to Sound(
        "water_stream",
        R.string.water_stream,
        R.string.sound_group__waterfront
      ),
      "white_noise" to Sound("white_noise", R.string.white_noise, R.string.sound_group__raw_noise),
      "wind_chimes_of_shells" to Sound(
        "wind_chimes_of_shells",
        R.string.wind_in_chimes_of_shells,
        R.string.sound_group__wind
      ),
      "wind_in_palm_trees" to Sound(
        "wind_in_palm_trees",
        R.string.wind_in_palm_trees,
        R.string.sound_group__wind
      )
    )

    /**
     * A helper function to keep it lean
     */
    fun get(key: String) = requireNotNull(LIBRARY[key])
  }
}
