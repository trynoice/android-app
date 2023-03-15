package com.github.ashutoshgngwr.noice.models

import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.lang.reflect.Type
import java.util.*
import kotlin.math.roundToInt

data class Preset(
  val id: String,
  val name: String,
  val soundStates: SortedMap<String, Float>,
) : Serializable {

  constructor(name: String, sounds: SortedMap<String, Float>)
    : this(UUID.randomUUID().toString(), name, sounds)

  companion object {
    val GSON_TYPE_SOUND_STATES: Type = TypeToken
      .getParameterized(SortedMap::class.java, String::class.java, Float::class.javaObjectType)
      .type
  }
}

/**
 * Sample JSON:
 *
 * [{
 *   "id": "test-id",
 *   "name": "test",
 *   "playerStates": [{
 *     "soundKey": "test-1",
 *     "volume": 13
 *   }]
 * }]
 */
data class PresetV2(
  val id: String,
  val name: String,
  val playerStates: List<PlayerState>,
) : Serializable {

  constructor(name: String, playerStates: List<PlayerState>)
    : this(UUID.randomUUID().toString(), name, playerStates)

  /**
   * Converts this [PresetV2] to [Preset].
   */
  fun toPresetV3(): Preset {
    val soundsV3 = playerStates.associate { it.soundId to (it.volume / 25F) }.toSortedMap()
    return Preset(id = id, name = name, soundStates = soundsV3)
  }

  companion object {
    val GSON_TYPE_PLAYER_STATES: Type = TypeToken
      .getParameterized(List::class.java, PlayerState::class.java)
      .type
  }

  data class PlayerState(val soundId: String, val volume: Int) : Serializable
}

/**
 * Sample JSON:
 *
 * [{
 *   "id": "test-id",
 *   "name": "test",
 *   "playerStates": [{
 *     "soundKey": "test-1",
 *     "timePeriod": 60,
 *     "volume": 13
 *   }]
 * }]
 */
data class PresetV1(
  val id: String,
  val name: String,
  val playerStates: List<PlayerState>,
) {

  /**
   * Converts this [PresetV1] to [PresetV2].
   */
  fun toPresetV2(): PresetV2 {
    val playerStatesV2 = mutableListOf<PresetV2.PlayerState>()
    val soundIds = mutableSetOf<String>()
    playerStates.forEach { playerStateV1 ->
      // v2 sound library merged many v1 sounds into a single sound.
      val id = v1SoundKeyToV2SoundIdMapping[playerStateV1.soundKey]
      if (id != null && soundIds.add(id)) {
        playerStatesV2.add(PresetV2.PlayerState(soundId = id, volume = playerStateV1.volume))
      }
    }

    playerStatesV2.sortBy { it.soundId }
    return PresetV2(id = id, name = name, playerStates = playerStatesV2)
  }

  companion object {
    private val v1SoundKeyToV2SoundIdMapping = mapOf(
      "airplane_inflight" to "air_travel",
      "airplane_seatbelt_beeps" to "air_travel",
      "birds" to "birds",
      "bonfire" to "campfire",
      "brownian_noise" to "brownian_noise",
      "coffee_shop" to "coffee_shop",
      "creaking_ship" to "creaking_boat",
      "crickets" to "crickets",
      "distant_thunder" to "thunder",
      "electric_car" to "electric_car",
      "heavy_rain" to "rain",
      "howling_wolf" to "wolves",
      "human_heartbeat" to "heartbeat",
      "light_rain" to "rain",
      "moderate_rain" to "rain",
      "morning_in_a_village" to "village_morning",
      "moving_train" to "train",
      "night" to "night",
      "office" to "office",
      "pink_noise" to "pink_noise",
      "public_library" to "public_library",
      "purring_cat" to "purring_cat",
      "quiet_conversation" to "quiet_conversations",
      "rolling_thunder" to "thunder",
      "screeching_seagulls" to "seagulls",
      "seaside" to "seashore",
      "soft_wind" to "soft_wind",
      "thunder_crack" to "thunder",
      "train_horn" to "train",
      "walking_through_the_snow" to "walking_in_snow",
      "water_stream" to "water_stream",
      "white_noise" to "white_noise",
      "wind_chimes_of_shells" to "wind_chimes",
      "wind_in_palm_trees" to "wind_through_palm_trees",
    )
  }

  data class PlayerState(
    val soundKey: String,
    val volume: Int,
    val timePeriod: Int,
  )
}

/**
 * Sample JSON:
 *
 * [{
 *   "a": "test",
 *   "b": [{
 *     "a": "test-1",
 *     "c": 30,
 *     "b": 0.75
 *   }]
 * }]
 */
data class PresetV0(
  @SerializedName("a") val name: String,
  @SerializedName("b") val playerStates: List<PlayerState>,
) {

  /**
   * Converts this [PresetV0] to [PresetV1].
   */
  fun toPresetV1(): PresetV1 {
    return PresetV1(
      id = UUID.randomUUID().toString(),
      name = name,
      playerStates = playerStates
        .sortedBy { it.soundKey }
        .map {
          PresetV1.PlayerState(
            soundKey = it.soundKey,
            volume = (it.volume * 25).roundToInt(),
            timePeriod = it.timePeriod + 30,
          )
        },
    )
  }

  data class PlayerState(
    @SerializedName("a") val soundKey: String,
    @SerializedName("b") val volume: Double,
    @SerializedName("c") val timePeriod: Int,
  )
}
