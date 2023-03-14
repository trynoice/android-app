package com.github.ashutoshgngwr.noice.model

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
