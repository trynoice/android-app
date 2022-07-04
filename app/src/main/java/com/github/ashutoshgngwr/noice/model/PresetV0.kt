package com.github.ashutoshgngwr.noice.model

import com.github.ashutoshgngwr.noice.engine.Player
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.util.*
import kotlin.math.roundToInt

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
  @Expose @SerializedName("a") val name: String,
  @Expose @SerializedName("b") val playerStates: List<PlayerState>,
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
            volume = (it.volume * Player.MAX_VOLUME).roundToInt(),
            timePeriod = it.timePeriod + 30,
          )
        },
    )
  }

  data class PlayerState(
    @Expose @SerializedName("a") val soundKey: String,
    @Expose @SerializedName("b") val volume: Double,
    @Expose @SerializedName("c") val timePeriod: Int,
  )
}
