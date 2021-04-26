package com.github.ashutoshgngwr.noice.sound

import android.content.SharedPreferences
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.google.gson.annotations.Expose
import java.util.*
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * [Preset] is the serialization class to save player states onto the persistent storage using
 * Android's [SharedPreferences].
 */
class Preset(
  @Expose val id: String,
  @Expose var name: String,
  @Expose val playerStates: Array<PlayerState>
) {

  init {
    // for stable equality operations
    playerStates.sortBy { T -> T.soundKey }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (other !is Preset) {
      return false
    }

    // id and name need not be equal. playbackStates should be
    return playerStates.contentEquals(other.playerStates)
  }

  override fun hashCode(): Int {
    // auto-generated
    var result = name.hashCode()
    result = 31 * result + playerStates.contentHashCode()
    return result
  }

  /**
   * [PlayerState] can hold the state properties [key, volume, timePeriod] of a [Player] instance.
   * Used for JSON encoding.
   */
  data class PlayerState(
    @Expose val soundKey: String, @Expose val volume: Int, @Expose val timePeriod: Int
  )

  companion object {
    /**
     * [from] exposes the primary constructor of [Preset] class. It automatically infers the
     * [PlayerState]s from provided [Collection] of [Player] instances.
     */
    fun from(name: String, players: Collection<Player>): Preset {
      val playerStates = arrayListOf<PlayerState>()
      for (player in players) {
        playerStates.add(PlayerState(player.soundKey, player.volume, player.timePeriod))
      }

      return Preset(UUID.randomUUID().toString(), name, playerStates.toTypedArray())
    }

    /**
     * [random] generates a nameless random preset using the provided sound [tag] and [intensity].
     * If sound [tag] is null, full library is considered for randomly selecting sounds for the
     * preset. If it is non-null, only sounds containing the provided tag are considered.
     * [intensity] is a [IntRange] that hints the lower and upper bounds for the number of sounds
     * present in the generated preset. A number is chosen randomly in this range.
     */
    fun random(tag: Sound.Tag?, intensity: IntRange): Preset {
      val library = Sound.filterLibraryByTag(tag).shuffled()
      val playerStates = mutableListOf<PlayerState>()
      for (i in 0 until Random.nextInt(intensity)) {
        val volume = 1 + Random.nextInt(0, Player.MAX_VOLUME)
        val timePeriod = Random.nextInt(Player.MIN_TIME_PERIOD, Player.MAX_TIME_PERIOD + 1)
        playerStates.add(PlayerState(library[i], volume, timePeriod))
      }

      return Preset(UUID.randomUUID().toString(), "", playerStates.toTypedArray())
    }
  }
}
