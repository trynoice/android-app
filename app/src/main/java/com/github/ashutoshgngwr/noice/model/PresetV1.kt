package com.github.ashutoshgngwr.noice.model

import com.google.gson.annotations.Expose

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
  @Expose val id: String,
  @Expose val name: String,
  @Expose val playerStates: List<PlayerState>,
) {

  /**
   * Converts this [PresetV1] to [Preset].
   */
  fun toPresetV2(): Preset {
    val playerStatesV2 = mutableListOf<com.github.ashutoshgngwr.noice.model.PlayerState>()
    val soundIds = mutableSetOf<String>()
    playerStates.forEach { playerStateV1 ->
      // v2 sound library merged many v1 sounds into a single sound.
      val id = v1SoundKeyToV2SoundIdMapping[playerStateV1.soundKey]
      if (id != null && soundIds.add(id)) {
        playerStatesV2.add(PlayerState(soundId = id, volume = playerStateV1.volume))
      }
    }

    playerStatesV2.sortBy { it.soundId }
    return Preset(name = name, playerStates = playerStatesV2.toTypedArray())
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
    @Expose val soundKey: String,
    @Expose val volume: Int,
    @Expose val timePeriod: Int,
  )
}
