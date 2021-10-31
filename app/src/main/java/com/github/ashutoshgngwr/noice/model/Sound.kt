package com.github.ashutoshgngwr.noice.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.ashutoshgngwr.noice.R

/**
 *  A data class that holds reference to a sound effect's asset path, title
 *  resource id from android resources and whether or not is it looping.
 *  It also declares a static instance 'LIBRARY' that defines the complete
 *  sound library supported by Noice.
 *
 *  @param src relative file paths of the sound sources in the app assets
 *  @param titleResID display title
 *  @param displayGroupResID display category of the sound
 *  @param isLooping if the [Sound] should loop when played
 */
class Sound private constructor(
  val src: Array<String>,
  @StringRes val titleResID: Int,
  @StringRes val displayGroupResID: Int,
  @DrawableRes val iconID: Int,
  val isLooping: Boolean = true,
  val tags: Array<Tag> = emptyArray()
) {

  enum class Tag { FOCUS, RELAX }

  companion object {
    /**
     * Static sound library with various effects. All files in [src] array are looked up in the assets.
     */
    val LIBRARY = mapOf(
      "airplane_inflight" to Sound(
        src = arrayOf("airplane_inflight.mp3"),
        titleResID = R.string.airplane_inflight,
        displayGroupResID = R.string.sound_group__vehicles,
        iconID = R.drawable.ic_sound_airplane_inflight,
      ),
      "airplane_seatbelt_beeps" to Sound(
        src = arrayOf("airplane_seatbelt_beeps.mp3"),
        titleResID = R.string.airplane_seatbelt_beeps,
        displayGroupResID = R.string.sound_group__vehicles,
        iconID = R.drawable.ic_sound_airplane_seatbelt_beeps,
        isLooping = false
      ),
      "birds" to Sound(
        src = arrayOf("birds_0.mp3", "birds_1.mp3"),
        titleResID = R.string.birds,
        displayGroupResID = R.string.sound_group__life,
        iconID = R.drawable.ic_sound_birds,
        tags = arrayOf(Tag.RELAX)
      ),
      "bonfire" to Sound(
        src = arrayOf("bonfire_0.mp3", "bonfire_1.mp3"),
        titleResID = R.string.bonfire,
        displayGroupResID = R.string.sound_group__public_gatherings,
        iconID = R.drawable.ic_sound_bonfire,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "brownian_noise" to Sound(
        src = arrayOf("brownian_noise.mp3"),
        titleResID = R.string.brownian_noise,
        displayGroupResID = R.string.sound_group__raw_noise,
        iconID = R.drawable.ic_sound_brownian_noise,
      ),
      "brown_plus_pink_waves" to Sound(
        src = arrayOf("brown_plus_pink_waves.mp3"),
        titleResID = R.string.brown_plus_pink_waves,
        displayGroupResID = R.string.sound_group__raw_noise,
        iconID = R.drawable.ic_sound_seaside,
      ),
      "coffee_shop" to Sound(
        src = arrayOf("coffee_shop_0.mp3", "coffee_shop_1.mp3"),
        titleResID = R.string.coffee_shop,
        displayGroupResID = R.string.sound_group__public_gatherings,
        iconID = R.drawable.ic_sound_coffee_shop,
        tags = arrayOf(Tag.FOCUS)
      ),
      "creaking_ship" to Sound(
        src = arrayOf("creaking_ship_0.mp3", "creaking_ship_1.mp3"),
        titleResID = R.string.creaking_ship,
        displayGroupResID = R.string.sound_group__vehicles,
        iconID = R.drawable.ic_sound_creaking_ship,
      ),
      "crickets" to Sound(
        src = arrayOf("crickets.mp3"),
        titleResID = R.string.crickets,
        displayGroupResID = R.string.sound_group__life,
        iconID = R.drawable.ic_sound_crickets,
        tags = arrayOf(Tag.RELAX)
      ),
      "distant_thunder" to Sound(
        src = arrayOf("distant_thunder.mp3"),
        titleResID = R.string.distant_thunder,
        displayGroupResID = R.string.sound_group__monsoon,
        iconID = R.drawable.ic_sound_distant_thunder,
        isLooping = false,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "electric_car" to Sound(
        src = arrayOf("electric_car_0.mp3", "electric_car_1.mp3"),
        titleResID = R.string.electric_car,
        displayGroupResID = R.string.sound_group__vehicles,
        iconID = R.drawable.ic_sound_electric_car
      ),
      "heavy_rain" to Sound(
        src = arrayOf("heavy_rain.mp3"),
        titleResID = R.string.heavy_rain,
        displayGroupResID = R.string.sound_group__monsoon,
        iconID = R.drawable.ic_sound_heavy_rain,
        tags = arrayOf(Tag.RELAX)
      ),
      "howling_wolf" to Sound(
        src = arrayOf("howling_wolf.mp3"),
        titleResID = R.string.howling_wolf,
        displayGroupResID = R.string.sound_group__life,
        iconID = R.drawable.ic_sound_howling_wolf,
        isLooping = false
      ),
      "human_heartbeat" to Sound(
        src = arrayOf("human_heartbeat.mp3"),
        titleResID = R.string.human_heartbeat,
        displayGroupResID = R.string.sound_group__life,
        iconID = R.drawable.ic_sound_human_heartbeat
      ),
      "light_rain" to Sound(
        src = arrayOf("light_rain.mp3"),
        titleResID = R.string.light_rain,
        displayGroupResID = R.string.sound_group__monsoon,
        iconID = R.drawable.ic_sound_light_rain,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "moderate_rain" to Sound(
        src = arrayOf("moderate_rain.mp3"),
        titleResID = R.string.moderate_rain,
        displayGroupResID = R.string.sound_group__monsoon,
        iconID = R.drawable.ic_sound_moderate_rain,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "morning_in_a_village" to Sound(
        src = arrayOf("morning_in_a_village.mp3"),
        titleResID = R.string.morning_in_a_village,
        displayGroupResID = R.string.sound_group__times_of_day,
        iconID = R.drawable.ic_sound_morning_in_a_village,
        tags = arrayOf(Tag.RELAX)
      ),
      "moving_train" to Sound(
        src = arrayOf("moving_train.mp3"),
        titleResID = R.string.moving_train,
        displayGroupResID = R.string.sound_group__vehicles,
        iconID = R.drawable.ic_sound_moving_train,
        tags = arrayOf(Tag.FOCUS)
      ),
      "night" to Sound(
        src = arrayOf("night_0.mp3", "night_1.mp3"),
        titleResID = R.string.night,
        displayGroupResID = R.string.sound_group__times_of_day,
        iconID = R.drawable.ic_sound_night,
        tags = arrayOf(Tag.RELAX)
      ),
      "office" to Sound(
        src = arrayOf("office_0.mp3", "office_1.mp3"),
        titleResID = R.string.office,
        displayGroupResID = R.string.sound_group__public_gatherings,
        iconID = R.drawable.ic_sound_office,
        tags = arrayOf(Tag.FOCUS)
      ),
      "pink_noise" to Sound(
        src = arrayOf("pink_noise.mp3"),
        titleResID = R.string.pink_noise,
        displayGroupResID = R.string.sound_group__raw_noise,
        iconID = R.drawable.ic_sound_pink_noise
      ),
      "public_library" to Sound(
        src = arrayOf("public_library_0.mp3", "public_library_1.mp3"),
        titleResID = R.string.public_library,
        displayGroupResID = R.string.sound_group__public_gatherings,
        iconID = R.drawable.ic_sound_public_library,
        tags = arrayOf(Tag.FOCUS)
      ),
      "purring_cat" to Sound(
        src = arrayOf("purring_cat.mp3"),
        titleResID = R.string.purring_cat,
        displayGroupResID = R.string.sound_group__life,
        iconID = R.drawable.ic_sound_purring_cat,
      ),
      "quiet_conversation" to Sound(
        src = arrayOf("quiet_conversation_0.mp3", "quiet_conversation_1.mp3"),
        titleResID = R.string.quiet_conversation,
        displayGroupResID = R.string.sound_group__public_gatherings,
        iconID = R.drawable.ic_sound_quiet_conversation,
        tags = arrayOf(Tag.FOCUS)
      ),
      "rolling_thunder" to Sound(
        src = arrayOf("rolling_thunder.mp3"),
        titleResID = R.string.rolling_thunder,
        displayGroupResID = R.string.sound_group__monsoon,
        iconID = R.drawable.ic_sound_rolling_thunder,
        isLooping = false,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "screeching_seagulls" to Sound(
        src = arrayOf("screeching_seagulls.mp3"),
        titleResID = R.string.screeching_seagulls,
        displayGroupResID = R.string.sound_group__life,
        iconID = R.drawable.ic_sound_screeching_seagulls,
        isLooping = false
      ),
      "seaside" to Sound(
        src = arrayOf("seaside.mp3"),
        titleResID = R.string.seaside,
        displayGroupResID = R.string.sound_group__water,
        iconID = R.drawable.ic_sound_seaside,
        tags = arrayOf(Tag.RELAX)
      ),
      "soft_wind" to Sound(
        src = arrayOf("soft_wind_0.mp3", "soft_wind_1.mp3"),
        titleResID = R.string.soft_wind,
        displayGroupResID = R.string.sound_group__wind,
        iconID = R.drawable.ic_sound_soft_wind,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "thunder_crack" to Sound(
        src = arrayOf("thunder_crack.mp3"),
        titleResID = R.string.thunder_crack,
        displayGroupResID = R.string.sound_group__monsoon,
        iconID = R.drawable.ic_sound_thunder_crack,
        isLooping = false,
        tags = arrayOf(Tag.RELAX)
      ),
      "train_horn" to Sound(
        src = arrayOf("train_horn.mp3"),
        titleResID = R.string.train_horn,
        displayGroupResID = R.string.sound_group__vehicles,
        iconID = R.drawable.ic_sound_train_horn,
        isLooping = false,
        tags = arrayOf(Tag.RELAX)
      ),
      "walking_through_the_snow" to Sound(
        src = arrayOf("walking_through_the_snow.mp3"),
        titleResID = R.string.walking_through_the_snow,
        displayGroupResID = R.string.sound_group__life,
        iconID = R.drawable.ic_sound_walking_through_the_snow,
        tags = arrayOf(Tag.RELAX)
      ),
      "water_hose" to Sound(
        src = arrayOf("water_hose_0.mp3", "water_hose_1.mp3"),
        titleResID = R.string.water_hose,
        displayGroupResID = R.string.sound_group__water,
        iconID = R.drawable.ic_sound_water_hose,
      ),
      "water_stream" to Sound(
        src = arrayOf("water_stream_0.mp3", "water_stream_1.mp3"),
        titleResID = R.string.water_stream,
        displayGroupResID = R.string.sound_group__water,
        iconID = R.drawable.ic_sound_water_stream,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "white_noise" to Sound(
        src = arrayOf("white_noise.mp3"),
        titleResID = R.string.white_noise,
        displayGroupResID = R.string.sound_group__raw_noise,
        iconID = R.drawable.ic_sound_white_noise
      ),
      "wind_chimes_of_shells" to Sound(
        src = arrayOf("wind_chimes_of_shells.mp3"),
        titleResID = R.string.wind_in_chimes_of_shells,
        displayGroupResID = R.string.sound_group__wind,
        iconID = R.drawable.ic_sound_wind_in_chimes_of_shells
      ),
      "wind_in_palm_trees" to Sound(
        src = arrayOf("wind_in_palm_trees_0.mp3", "wind_in_palm_trees_1.mp3"),
        titleResID = R.string.wind_in_palm_trees,
        displayGroupResID = R.string.sound_group__wind,
        iconID = R.drawable.ic_sound_wind_in_palm_trees,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      )
    )

    /**
     * A helper function to keep it lean
     */
    fun get(key: String) = requireNotNull(LIBRARY[key])

    /**
     * [filterLibraryByTag] returns the keys whose [Sound] contain the given [tag]. If the given
     * [tag] is empty, it returns all keys from the library.
     */
    fun filterLibraryByTag(tag: Tag?): Collection<String> {
      tag ?: return LIBRARY.keys
      return LIBRARY.filter { it.value.tags.contains(tag) }.map { it.key }
    }
  }
}
