package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.Utils.withGson
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.Expose
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

// curious about the weird serialized names? see https://github.com/ashutoshgngwr/noice/issues/110
// and https://github.com/ashutoshgngwr/noice/pulls/117
/**
 * [Preset] is the serialization class to save player states onto the persistent storage using
 * Android's [SharedPreferences].
 */
class Preset private constructor(
  @Expose @SerializedName("a") var name: String,
  @Expose @SerializedName("b") val playerStates: Array<PlayerState>
) {

  /**
   * [PlayerState] can hold the state properties [key, volume, timePeriod] of a [Player] instance.
   * Used for JSON encoding.
   */
  data class PlayerState(
    @Expose @SerializedName("a") val soundKey: String,
    @Expose @SerializedName("b") @JsonAdapter(value = VolumeSerializer::class) val volume: Int,
    @Expose @SerializedName("c") @JsonAdapter(value = TimePeriodSerializer::class) val timePeriod: Int
  )

  /**
   * [VolumeSerializer] is a fix for maintaining backward compatibility with versions older than
   * 0.3.0. Volume was written as a Float to persistent storage in older versions.
   * Switching to Integer in newer version was causing crash if the user had any saved presets.
   */
  private class VolumeSerializer : JsonSerializer<Int>, JsonDeserializer<Int> {

    override fun serialize(src: Int, type: Type, ctx: JsonSerializationContext) =
      JsonPrimitive(src.toFloat() / Player.MAX_VOLUME)

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext) =
      (json.asFloat * Player.MAX_VOLUME).toInt()

  }

  /**
   * [TimePeriodSerializer] is a fix for for offset corrections introduced in the following commits.
   * 1. https://github.com/ashutoshgngwr/noice/commit/b449ef643227b65685b71f7780a814c606e6abad
   * 2. https://github.com/ashutoshgngwr/noice/commit/8ef502debd84aeadadaa665acd37c3cee592f521
   * 3. https://github.com/ashutoshgngwr/noice/commit/11eb63ee22f3a03eca982cbd308f06ec164ab300#diff-db23b0e75244cdeb8ead7184bb06cb7cR15-R71
   *
   * Essentially it serializes the time period value as it would be seen on the SeekBar, i.e. in
   * range [0, [Player.MAX_TIME_PERIOD] - [Player.MIN_TIME_PERIOD]]. On deserialization, it again
   * corrects the offset to ensure that time period is in the range
   * [[Player.MIN_TIME_PERIOD], [Player.MAX_TIME_PERIOD]].
   */
  private class TimePeriodSerializer : JsonSerializer<Int>, JsonDeserializer<Int> {

    override fun serialize(src: Int, type: Type, ctx: JsonSerializationContext) =
      JsonPrimitive(src - Player.MIN_TIME_PERIOD)

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext) =
      json.asInt + Player.MIN_TIME_PERIOD

  }

  companion object {
    private const val PREF_PRESETS = "presets"

    /**
     * [from] exposes the primary constructor of [Preset] class. It automatically infers the
     * [PlayerState]s from provided [Collection] of [Player] instances.
     */
    fun from(name: String, players: Collection<Player>): Preset {
      val playerStates = arrayListOf<PlayerState>()
      for (player in players) {
        playerStates.add(PlayerState(player.soundKey, player.volume, player.timePeriod))
      }

      return Preset(name, playerStates.toTypedArray())
    }

    /**
     * [readAllFromUserPreferences] reads the serialized version of [Preset]s from the persistent
     * storage and returns an [ArrayList].
     */
    fun readAllFromUserPreferences(context: Context): Array<Preset> = withGson {
      PreferenceManager.getDefaultSharedPreferences(context).let { prefs ->
        it.fromJson(prefs.getString(PREF_PRESETS, "[]"), Array<Preset>::class.java)
      }
    }

    /**
     * [writeAllToUserPreferences] will overwrite the current collection of [Preset]s by the
     * given [ArrayList] in the persistent storage.
     */
    fun writeAllToUserPreferences(context: Context, presets: Collection<Preset>) {
      withGson {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
          putString(PREF_PRESETS, it.toJson(presets))
        }
      }
    }

    /**
     * [appendToUserPreferences] appends the given [Preset] to the collection of [Preset]s already
     * present in the persistent storage.
     */
    fun appendToUserPreferences(context: Context, preset: Preset) {
      readAllFromUserPreferences(context).also {
        writeAllToUserPreferences(context, listOf(*it, preset))
      }
    }

    /**
     * [duplicateNameValidator] returns a lambda function that can be used to check whether a preset
     * with the given name exists in the persistent state. The lambda uses Preset names from an
     * in-memory cached state. This state is initialized at the time of its creation.
     */
    fun duplicateNameValidator(context: Context): (String) -> Boolean {
      val presetNames = readAllFromUserPreferences(context).map { it.name }.toHashSet()
      return { it in presetNames }
    }

    /**
     * [findByName] searches the given name in the persistent storage. Returns a [Preset] if it
     * finds one with the given name. Returns null otherwise.
     */
    fun findByName(context: Context, name: String): Preset? {
      readAllFromUserPreferences(context).forEach {
        if (it.name == name) {
          return it
        }
      }

      return null
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

    // name need not be equal. playbackStates should be
    return playerStates.contentEquals(other.playerStates)
  }

  override fun hashCode(): Int {
    // auto-generated
    var result = name.hashCode()
    result = 31 * result + playerStates.contentHashCode()
    return result
  }
}
