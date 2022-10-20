package com.github.ashutoshgngwr.noice.model

import java.io.Serializable
import java.util.*

/**
 * [Preset] represents a snapshot of Player Manager at any given time. It holds states of various
 * players and is used for serializing the state to JSON and persisting it on disk.
 */
data class Preset(
  val id: String,
  val name: String,
  val playerStates: Array<PlayerState>,
) : Serializable {

  constructor(name: String, playerStates: Array<PlayerState>)
    : this(UUID.randomUUID().toString(), name, playerStates)

  init {
    playerStates.sortBy { it.soundId }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (other !is Preset) {
      return false
    }

    return id == other.id
      && name == other.name
      && playerStates.contentEquals(other.playerStates)
  }

  override fun hashCode(): Int {
    // auto-generated
    var result = name.hashCode()
    result = 31 * result + playerStates.contentHashCode()
    return result
  }

  /**
   * Returns whether the given [playerStates] constitute this preset.
   */
  fun hasMatchingPlayerStates(playerStates: Array<PlayerState>): Boolean {
    playerStates.sortBy { it.soundId }
    return this.playerStates.contentEquals(playerStates)
  }
}
