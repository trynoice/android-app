package com.github.ashutoshgngwr.noice.model

import android.net.Uri
import com.github.ashutoshgngwr.noice.playback.Player
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import java.util.*

/**
 * [Preset] represents a snapshot of Player Manager at any given time. It holds states of various
 * players and is used for serializing the state to JSON and persisting it on disk.
 */
data class Preset(
  @Expose val id: String,
  @Expose var name: String,
  @Expose val playerStates: Array<PlayerState>
) {

  companion object {
    private const val URI_SCHEME = "https"
    private const val URI_AUTHORITY = "ashutoshgngwr.github.io"
    private const val URI_PATH = "/noice/preset"
    internal const val URI_NAME_PARAM = "name"
    internal const val URI_PLAYER_STATES_PARAM = "playerStates"

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
     * Decodes a preset from an [Uri] encoded using [toUri].
     */
    fun from(uri: Uri, gson: Gson): Preset {
      val name = uri.getQueryParameter(URI_NAME_PARAM) ?: ""
      val playerStatesJSON = uri.getQueryParameter(URI_PLAYER_STATES_PARAM)
        ?: throw IllegalArgumentException("'playerStates' query parameter is missing from the URI.")

      val playerStates = gson.fromJson(playerStatesJSON, Array<PlayerState>::class.java)
      return Preset(UUID.randomUUID().toString(), name, playerStates)
    }
  }

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
   * Encodes the preset to an [Uri] with [name] and [playerStates] as query its parameters.
   */
  fun toUri(gson: Gson): Uri {
    return Uri.Builder()
      .scheme(URI_SCHEME)
      .authority(URI_AUTHORITY)
      .path(URI_PATH)
      .appendQueryParameter(URI_NAME_PARAM, name)
      .appendQueryParameter(URI_PLAYER_STATES_PARAM, gson.toJson(playerStates))
      .build()
  }

  /**
   * [PlayerState] can hold the state properties [key, volume, timePeriod] of a [Player] instance.
   * Used for JSON encoding.
   */
  data class PlayerState(
    @Expose val soundKey: String, @Expose val volume: Int, @Expose val timePeriod: Int
  )
}
