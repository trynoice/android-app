package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
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
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Type
import java.util.*
import kotlin.math.round

/**
 * [Preset] is the serialization class to save player states onto the persistent storage using
 * Android's [SharedPreferences].
 */
class Preset(@Expose var name: String, @Expose val playerStates: Array<PlayerState>) {

  @Expose
  @JsonAdapter(value = IDSerializer::class)
  val id: String = ""

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

  /**
   * [IDSerializer] assigns an UUID to new presets before serializing them to JSON. If a preset
   * already has an UUID, [IDSerializer] doesn't do anything. Deserializer implementation is
   * required; otherwise Gson attempts to deserialize the field as an object rather than a [String].
   */
  private class IDSerializer : JsonSerializer<String>, JsonDeserializer<String> {
    override fun serialize(src: String, type: Type, ctx: JsonSerializationContext): JsonElement {
      if (src.isBlank()) {
        return JsonPrimitive(UUID.randomUUID().toString())
      }

      return JsonPrimitive(src)
    }

    override fun deserialize(e: JsonElement, type: Type, ctx: JsonDeserializationContext): String {
      return e.asString ?: ""
    }
  }

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_V0 = "presets"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_V1 = "presets.v1"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREFERENCE_KEY = PREF_V1

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
        it.fromJson(prefs.getString(PREFERENCE_KEY, "[]"), Array<Preset>::class.java)
      }
    }

    /**
     * [writeAllToUserPreferences] will overwrite the current collection of [Preset]s by the
     * given [ArrayList] in the persistent storage.
     */
    fun writeAllToUserPreferences(context: Context, presets: Collection<Preset>) {
      withGson {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
          putString(PREFERENCE_KEY, it.toJson(presets))
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

    /**
     * [migrateAllToV1] migrates saved user presets from its old definitions to its current
     * definitions.
     *
     * Old definitions can be found here:
     * https://github.com/ashutoshgngwr/noice/blob/2fe643b655e1609f2c857226f6bcfcbf4ece6edd/app/src/main/java/com/github/ashutoshgngwr/noice/sound/Preset.kt
     *
     * To sum it up, there are four migration goals:
     * 1. Assign stable IDs to saved presets
     * 2. Clean up the naming mess https://github.com/ashutoshgngwr/noice/issues/110
     * 3. Fix volume type issues https://github.com/ashutoshgngwr/noice/pull/105
     * 4. Fix time period offset issues introduced in following commits
     *    - https://github.com/ashutoshgngwr/noice/commit/b449ef643227b65685b71f7780a814c606e6abad
     *    - https://github.com/ashutoshgngwr/noice/commit/8ef502debd84aeadadaa665acd37c3cee592f521
     *    - https://github.com/ashutoshgngwr/noice/commit/11eb63ee22f3a03eca982cbd308f06ec164ab300#diff-db23b0e75244cdeb8ead7184bb06cb7cR15-R71
     */
    fun migrateAllToV1(ctx: Context) {
      val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
      val json = prefs.getString(PREF_V0, null)
      if (json.isNullOrBlank()) {
        return
      }

      val presets = mutableListOf<Preset>()
      JSONArray(json).forEach<JSONObject> { preset ->
        val presetName = preset.getString("a")
        val playerStates = mutableListOf<PlayerState>()

        preset.getJSONArray("b").forEach<JSONObject> { state ->
          playerStates.add(
            PlayerState(
              soundKey = state.getString("a"),
              // with corrections from the old deserializers
              volume = round(state.getDouble("b") * Player.MAX_VOLUME).toInt(),
              timePeriod = state.getInt("c") + Player.MIN_TIME_PERIOD
            )
          )
        }

        presets.add(Preset(presetName, playerStates.toTypedArray()))
      }

      writeAllToUserPreferences(ctx, presets)
      prefs.edit { remove(PREF_V0) }
    }

    /**
     * [forEach] iterator extension for [JSONArray] type
     */
    private inline fun <reified T> JSONArray.forEach(crossinline f: (T) -> Unit) {
      for (i in 0 until length()) {
        val obj = get(i)
        if (obj !is T) {
          throw ClassCastException("$obj cannot be cast as ${T::class.qualifiedName}")
        }

        f(get(i) as T)
      }
    }
  }
}
