package com.github.ashutoshgngwr.noice.model

import com.github.ashutoshgngwr.noice.playback.Player
import com.google.gson.annotations.Expose
import java.util.*

/**
 * [Preset] represents a snapshot of Player Manager at any given time. It holds states of various
 * players and is used for serializing the state to JSON and persisting it on disk.
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
  }
}
