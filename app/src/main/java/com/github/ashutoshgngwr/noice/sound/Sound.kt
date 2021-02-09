package com.github.ashutoshgngwr.noice.sound

import androidx.annotation.StringRes
import com.github.ashutoshgngwr.noice.R

/**
 *  A data class that holds reference to a sound effect's asset path, title
 *  resource id from android resources and whether or not is it looping.
 *  It also declares a static instance 'LIBRARY' that defines the complete
 *  sound library supported by Noice.
 *
 *  @param src relative file paths of the sound sources in the app assets
 *  @param titleResId display title
 *  @param displayGroupResID display category of the sound
 *  @param credits an array of [Pairs][Pair] with title and url of source files (displayed in About)
 *  @param isLooping if the [Sound] should loop when played
 */
class Sound private constructor(
  val src: Array<String>,
  @StringRes val titleResId: Int,
  @StringRes val displayGroupResID: Int,
  val credits: Array<Pair<Int, Int>>,
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
        arrayOf("airplane_inflight.mp3"),
        R.string.airplane_inflight,
        R.string.sound_group__vehicles,
        arrayOf(
          Pair(
            R.string.credits__sound_airplane_inflight,
            R.string.credits__sound_airplane_inflight_url
          )
        )
      ),
      "airplane_seatbelt_beeps" to Sound(
        arrayOf("airplane_seatbelt_beeps.mp3"),
        R.string.airplane_seatbelt_beeps,
        R.string.sound_group__vehicles,
        arrayOf(
          Pair(
            R.string.credits__sound_airplane_seatbelt_beeps,
            R.string.credits__sound_airplane_seatbelt_beeps_url
          )
        ),
        isLooping = false
      ),
      "birds" to Sound(
        arrayOf("birds_0.mp3", "birds_1.mp3"),
        R.string.birds,
        R.string.sound_group__life,
        arrayOf(
          Pair(R.string.credits__sound_birds_0, R.string.credits__sound_birds_0_url),
          Pair(R.string.credits__sound_birds_1, R.string.credits__sound_birds_1_url)
        ),
        tags = arrayOf(Tag.RELAX)
      ),
      "bonfire" to Sound(
        arrayOf("bonfire_0.mp3", "bonfire_1.mp3"),
        R.string.bonfire,
        R.string.sound_group__public_gatherings,
        arrayOf(Pair(R.string.credits__sound_bonfire, R.string.credits__sound_bonfire_url)),
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "brownian_noise" to Sound(
        arrayOf("brownian_noise.mp3"),
        R.string.brownian_noise,
        R.string.sound_group__raw_noise,
        arrayOf(
          Pair(
            R.string.credits__sound_brownian_noise,
            R.string.credits__sound_brownian_noise_url
          )
        )
      ),
      "coffee_shop" to Sound(
        arrayOf("coffee_shop_0.mp3", "coffee_shop_1.mp3"),
        R.string.coffee_shop,
        R.string.sound_group__public_gatherings,
        arrayOf(Pair(R.string.credits__sound_coffee_shop, R.string.credits__sound_coffee_shop_url)),
        tags = arrayOf(Tag.FOCUS)
      ),
      "creaking_ship" to Sound(
        arrayOf("creaking_ship_0.mp3", "creaking_ship_1.mp3"),
        R.string.creaking_ship,
        R.string.sound_group__vehicles,
        arrayOf(
          Pair(
            R.string.credits__sound_creaking_ship_0,
            R.string.credits__sound_creaking_ship_0_url
          ),
          Pair(
            R.string.credits__sound_creaking_ship_1,
            R.string.credits__sound_creaking_ship_1_url
          )
        )
      ),
      "crickets" to Sound(
        arrayOf("crickets.mp3"),
        R.string.crickets,
        R.string.sound_group__life,
        arrayOf(Pair(R.string.credits__sound_crickets, R.string.credits__sound_crickets_url)),
        tags = arrayOf(Tag.RELAX)
      ),
      "distant_thunder" to Sound(
        arrayOf("distant_thunder.mp3"),
        R.string.distant_thunder,
        R.string.sound_group__monsoon,
        arrayOf(
          Pair(R.string.credits__sound_distant_thunder, R.string.credits__sound_distant_thunder_url)
        ),
        isLooping = false,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "electric_car" to Sound(
        arrayOf("electric_car_0.mp3", "electric_car_1.mp3"),
        R.string.electric_car,
        R.string.sound_group__vehicles,
        arrayOf(
          Pair(R.string.credits__sound_electric_car, R.string.credits__sound_electric_car_url)
        )
      ),
      "heavy_rain" to Sound(
        arrayOf("heavy_rain.mp3"),
        R.string.heavy_rain,
        R.string.sound_group__monsoon,
        arrayOf(Pair(R.string.credits__sound_heavy_rain, R.string.credits__sound_heavy_rain_url)),
        tags = arrayOf(Tag.RELAX)
      ),
      "howling_wolf" to Sound(
        arrayOf("howling_wolf.mp3"),
        R.string.howling_wolf,
        R.string.sound_group__life,
        arrayOf(
          Pair(R.string.credits__sound_howling_wolf, R.string.credits__sound_howling_wolf_url)
        ),
        isLooping = false
      ),
      "human_heartbeat" to Sound(
        arrayOf("human_heartbeat.mp3"),
        R.string.human_heartbeat,
        R.string.sound_group__life,
        arrayOf(
          Pair(R.string.credits__sound_human_heartbeat, R.string.credits__sound_human_heartbeat_url)
        )
      ),
      "light_rain" to Sound(
        arrayOf("light_rain.mp3"),
        R.string.light_rain,
        R.string.sound_group__monsoon,
        arrayOf(Pair(R.string.credits__sound_light_rain, R.string.credits__sound_light_rain_url)),
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "moderate_rain" to Sound(
        arrayOf("moderate_rain.mp3"),
        R.string.moderate_rain,
        R.string.sound_group__monsoon,
        arrayOf(
          Pair(R.string.credits__sound_moderate_rain, R.string.credits__sound_moderate_rain_url)
        ),
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "morning_in_a_village" to Sound(
        arrayOf("morning_in_a_village.mp3"),
        R.string.morning_in_a_village,
        R.string.sound_group__times_of_day,
        arrayOf(
          Pair(
            R.string.credits__sound_morning_in_a_village,
            R.string.credits__sound_morning_in_a_village_url
          )
        ),
        tags = arrayOf(Tag.RELAX)
      ),
      "moving_train" to Sound(
        arrayOf("moving_train.mp3"),
        R.string.moving_train,
        R.string.sound_group__vehicles,
        arrayOf(
          Pair(R.string.credits__sound_moving_train, R.string.credits__sound_moving_train_url)
        ),
        tags = arrayOf(Tag.FOCUS)
      ),
      "night" to Sound(
        arrayOf("night_0.mp3", "night_1.mp3"),
        R.string.night,
        R.string.sound_group__times_of_day,
        arrayOf(Pair(R.string.credits__sound_night, R.string.credits__sound_night_url)),
        tags = arrayOf(Tag.RELAX)
      ),
      "office" to Sound(
        arrayOf("office_0.mp3", "office_1.mp3"),
        R.string.office,
        R.string.sound_group__public_gatherings,
        arrayOf(Pair(R.string.credits__sound_office, R.string.credits__sound_office_url)),
        tags = arrayOf(Tag.FOCUS)
      ),
      "pink_noise" to Sound(
        arrayOf("pink_noise.mp3"),
        R.string.pink_noise,
        R.string.sound_group__raw_noise,
        arrayOf()
      ),
      "public_library" to Sound(
        arrayOf("public_library_0.mp3", "public_library_1.mp3"),
        R.string.public_library,
        R.string.sound_group__public_gatherings,
        arrayOf(
          Pair(
            R.string.credits__sound_public_library_0,
            R.string.credits__sound_public_library_0_url
          ),
          Pair(
            R.string.credits__sound_public_library_1,
            R.string.credits__sound_public_library_1_url
          )
        ),
        tags = arrayOf(Tag.FOCUS)
      ),
      "purring_cat" to Sound(
        arrayOf("purring_cat.mp3"),
        R.string.purring_cat,
        R.string.sound_group__life,
        arrayOf(Pair(R.string.credits__sound_purring_cat, R.string.credits__sound_purring_cat_url))
      ),
      "quiet_conversation" to Sound(
        arrayOf("quiet_conversation_0.mp3", "quiet_conversation_1.mp3"),
        R.string.quiet_conversation,
        R.string.sound_group__public_gatherings,
        arrayOf(
          Pair(
            R.string.credits__sound_quiet_conversation,
            R.string.credits__sound_quiet_conversation_url
          )
        ),
        tags = arrayOf(Tag.FOCUS)
      ),
      "rolling_thunder" to Sound(
        arrayOf("rolling_thunder.mp3"),
        R.string.rolling_thunder,
        R.string.sound_group__monsoon,
        arrayOf(
          Pair(R.string.credits__sound_rolling_thunder, R.string.credits__sound_rolling_thunder_url)
        ),
        isLooping = false,
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "screeching_seagulls" to Sound(
        arrayOf("screeching_seagulls.mp3"),
        R.string.screeching_seagulls,
        R.string.sound_group__life,
        arrayOf(
          Pair(
            R.string.credits__sound_screeching_seagulls,
            R.string.credits__sound_screeching_seagulls_url
          )
        ),
        isLooping = false
      ),
      "seaside" to Sound(
        arrayOf("seaside.mp3"),
        R.string.seaside,
        R.string.sound_group__water,
        arrayOf(Pair(R.string.credits__sound_seaside, R.string.credits__sound_seaside_url)),
        tags = arrayOf(Tag.RELAX)
      ),
      "soft_wind" to Sound(
        arrayOf("soft_wind_0.mp3", "soft_wind_1.mp3"),
        R.string.soft_wind,
        R.string.sound_group__wind,
        arrayOf(Pair(R.string.credits__sound_soft_wind, R.string.credits__sound_soft_wind_url)),
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "thunder_crack" to Sound(
        arrayOf("thunder_crack.mp3"),
        R.string.thunder_crack,
        R.string.sound_group__monsoon,
        arrayOf(
          Pair(R.string.credits__sound_thunder_crack, R.string.credits__sound_thunder_crack_url)
        ),
        isLooping = false,
        tags = arrayOf(Tag.RELAX)
      ),
      "train_horn" to Sound(
        arrayOf("train_horn.mp3"),
        R.string.train_horn,
        R.string.sound_group__vehicles,
        arrayOf(Pair(R.string.credits__sound_train_horn, R.string.credits__sound_train_horn_url)),
        isLooping = false,
        tags = arrayOf(Tag.RELAX)
      ),
      "walking_through_the_snow" to Sound(
        arrayOf("walking_through_the_snow.mp3"),
        R.string.walking_through_the_snow,
        R.string.sound_group__life,
        arrayOf(
          Pair(
            R.string.credits__sound_walking_through_the_snow,
            R.string.credits__sound_walking_through_the_snow_url
          )
        ),
        tags = arrayOf(Tag.RELAX)
      ),
      "water_hose" to Sound(
        arrayOf("water_hose_0.mp3", "water_hose_1.mp3"),
        R.string.water_hose,
        R.string.sound_group__water,
        arrayOf(Pair(R.string.credits__sound_water_hose, R.string.credits__sound_water_hose_url))
      ),
      "water_stream" to Sound(
        arrayOf("water_stream_0.mp3", "water_stream_1.mp3"),
        R.string.water_stream,
        R.string.sound_group__water,
        arrayOf(
          Pair(
            R.string.credits__sound_water_stream,
            R.string.credits__sound_water_stream_url
          )
        ),
        tags = arrayOf(Tag.FOCUS, Tag.RELAX)
      ),
      "white_noise" to Sound(
        arrayOf("white_noise.mp3"),
        R.string.white_noise,
        R.string.sound_group__raw_noise,
        arrayOf()
      ),
      "wind_chimes_of_shells" to Sound(
        arrayOf("wind_chimes_of_shells.mp3"),
        R.string.wind_in_chimes_of_shells,
        R.string.sound_group__wind,
        arrayOf(
          Pair(
            R.string.credits__sound_wind_chimes_of_shells,
            R.string.credits__sound_wind_chimes_of_shells_url
          )
        )
      ),
      "wind_in_palm_trees" to Sound(
        arrayOf("wind_in_palm_trees_0.mp3", "wind_in_palm_trees_1.mp3"),
        R.string.wind_in_palm_trees,
        R.string.sound_group__wind,
        arrayOf(
          Pair(
            R.string.credits__sound_wind_in_palm_trees,
            R.string.credits__sound_wind_in_palm_trees_url
          )
        ),
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
