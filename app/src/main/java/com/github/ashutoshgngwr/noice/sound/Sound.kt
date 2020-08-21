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
  val src: Array<String>,
  @StringRes val titleResId: Int,
  @StringRes val displayGroupResID: Int,
  val isLooping: Boolean = true
) {

  companion object {
    /**
     * Static sound library with various effects. All files in [src] array are looked up in the assets.
     */
    val LIBRARY = mapOf(
      "airplane_inflight" to Sound(
        arrayOf("airplane_inflight.mp3"),
        R.string.airplane_inflight,
        R.string.sound_group__airplane
      ),
      "airplane_seatbelt_beeps" to Sound(
        arrayOf("airplane_seatbelt_beeps.mp3"),
        R.string.airplane_seatbelt_beeps, R.string.sound_group__airplane,
        false
      ),
      "birds" to Sound(
        arrayOf("birds.mp3"),
        R.string.birds,
        R.string.sound_group__jungle
      ),
      "bonfire" to Sound(
        arrayOf("bonfire_0.mp3", "bonfire_1.mp3"),
        R.string.bonfire,
        R.string.sound_group__jungle
      ),
      "brownian_noise" to Sound(
        arrayOf("brownian_noise.mp3"),
        R.string.brownian_noise,
        R.string.sound_group__raw_noise
      ),
      "coffee_shop" to Sound(
        arrayOf("coffee_shop_0.mp3", "coffee_shop_1.mp3"),
        R.string.coffee_shop,
        R.string.sound_group__public_gatherings
      ),
      "distant_thunder" to Sound(
        arrayOf("distant_thunder.mp3"),
        R.string.distant_thunder,
        R.string.sound_group__monsoon,
        false
      ),
      "heavy_rain" to Sound(
        arrayOf("heavy_rain.mp3"),
        R.string.heavy_rain,
        R.string.sound_group__monsoon
      ),
      "light_rain" to Sound(
        arrayOf("light_rain_0.mp3", "light_rain_1.mp3"),
        R.string.light_rain,
        R.string.sound_group__monsoon
      ),
      "moderate_rain" to Sound(
        arrayOf("moderate_rain.mp3"),
        R.string.moderate_rain,
        R.string.sound_group__monsoon
      ),
      "morning_in_a_village" to Sound(
        arrayOf("morning_in_a_village_0.mp3", "morning_in_a_village_1.mp3"),
        R.string.morning_in_a_village,
        R.string.sound_group__times_of_day
      ),
      "moving_train" to Sound(
        arrayOf("moving_train.mp3"),
        R.string.moving_train,
        R.string.sound_group__train
      ),
      "night" to Sound(
        arrayOf("night_0.mp3", "night_1.mp3"),
        R.string.night,
        R.string.sound_group__times_of_day
      ),
      "office" to Sound(
        arrayOf("office_0.mp3", "office_1.mp3"),
        R.string.office,
        R.string.sound_group__public_gatherings
      ),
      "pink_noise" to Sound(
        arrayOf("pink_noise.mp3"),
        R.string.pink_noise,
        R.string.sound_group__raw_noise
      ),
      "public_library" to Sound(
        arrayOf("public_library_0.mp3", "public_library_1.mp3"),
        R.string.public_library,
        R.string.sound_group__public_gatherings
      ),
      "rolling_thunder" to Sound(
        arrayOf("rolling_thunder.mp3"),
        R.string.rolling_thunder,
        R.string.sound_group__monsoon,
        false
      ),
      "seaside" to Sound(
        arrayOf("seaside_0.mp3", "seaside_1.mp3"),
        R.string.seaside,
        R.string.sound_group__waterfront
      ),
      "soft_wind" to Sound(
        arrayOf("soft_wind_0.mp3", "soft_wind_1.mp3"),
        R.string.soft_wind,
        R.string.sound_group__wind
      ),
      "thunder_crack" to Sound(
        arrayOf("thunder_crack.mp3"),
        R.string.thunder_crack,
        R.string.sound_group__monsoon,
        false
      ),
      "train_horn" to Sound(
        arrayOf("train_horn.mp3"),
        R.string.train_horn,
        R.string.sound_group__train,
        false
      ),
      "water_hose" to Sound(arrayOf("water_hose.mp3"), R.string.water_hose, R.string.sound_group__airplane),
      "water_hosing" to Sound(arrayOf("water_hosing.mp3"), R.string.water_hosing, R.string.sound_group__airplane, false),
      "water_stream" to Sound(
        arrayOf("water_stream_0.mp3", "water_stream_1.mp3"),
        R.string.water_stream,
        R.string.sound_group__waterfront
      ),
      "white_noise" to Sound(
        arrayOf("white_noise.mp3"),
        R.string.white_noise,
        R.string.sound_group__raw_noise
      ),
      "wind_chimes_of_shells" to Sound(
        arrayOf("wind_chimes_of_shells.mp3"),
        R.string.wind_in_chimes_of_shells,
        R.string.sound_group__wind
      ),
      "wind_in_palm_trees" to Sound(
        arrayOf("wind_in_palm_trees_0.mp3", "wind_in_palm_trees_1.mp3"),
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
