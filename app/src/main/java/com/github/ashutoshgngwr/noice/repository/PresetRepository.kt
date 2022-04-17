package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.Player
import com.github.ashutoshgngwr.noice.repository.PresetRepository.Companion.PREFERENCE_KEY
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * [PresetRepository] implements the data access layer for [Preset]. It stores all its data in a
 * shared preference with [PREFERENCE_KEY].
 */
@Singleton
class PresetRepository @Inject constructor(
  @ApplicationContext context: Context,
  private val gson: Gson,
) {

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_V0 = "presets"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_V1 = "presets.v1"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREFERENCE_KEY = PREF_V1

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXPORT_VERSION_KEY = "version"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXPORT_DATA_KEY = "data"

    private const val DEFAULT_PRESETS = """[{
      "id": "808feaed-f4ce-4d1e-9179-ae7aec31180e",
      "name": "Thunderstorm by @markwmuller",
      "playerStates": [
        {
          "soundKey": "distant_thunder",
          "timePeriod": 219,
          "volume": 20
        },
        {
          "soundKey": "heavy_rain",
          "timePeriod": 300,
          "volume": 20
        },
        {
          "soundKey": "rolling_thunder",
          "timePeriod": 294,
          "volume": 4
        },
        {
          "soundKey": "thunder_crack",
          "timePeriod": 337,
          "volume": 17
        }
      ]
    },
    {
      "id": "13006e01-9413-45d7-bffc-dc577b077d67",
      "name": "Beach by @eMPee584",
      "playerStates": [
        {
          "soundKey": "crickets",
          "timePeriod": 300,
          "volume": 6
        },
        {
          "soundKey": "screeching_seagulls",
          "timePeriod": 300,
          "volume": 6
        },
        {
          "soundKey": "seaside",
          "timePeriod": 300,
          "volume": 20
        },
        {
          "soundKey": "soft_wind",
          "timePeriod": 300,
          "volume": 6
        },
        {
          "soundKey": "wind_in_palm_trees",
          "timePeriod": 300,
          "volume": 15
        }
      ]
    },
    {
      "id": "b76ac285-1265-472c-bcdc-aecba3a28fa2",
      "name": "Camping by @ashutoshgngwr",
      "playerStates": [
        {
          "soundKey": "bonfire",
          "timePeriod": 300,
          "volume": 22
        },
        {
          "soundKey": "howling_wolf",
          "timePeriod": 964,
          "volume": 3
        },
        {
          "soundKey": "night",
          "timePeriod": 300,
          "volume": 6
        },
        {
          "soundKey": "quiet_conversation",
          "timePeriod": 300,
          "volume": 5
        },
        {
          "soundKey": "soft_wind",
          "timePeriod": 300,
          "volume": 8
        }
      ]
    }]"""
  }

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  init {
    migrate()
  }

  /**
   * Add the given preset to persistent storage.
   *
   * @throws IllegalArgumentException if [Preset.id] is missing
   */
  fun create(preset: Preset) {
    if (preset.id.isBlank()) {
      throw IllegalArgumentException("preset must contain a valid ID")
    }

    commit(arrayOf(*list(), preset))
  }

  /**
   * Lists all [Preset]s present in the persistent storage.
   */
  fun list(): Array<Preset> {
    return gson.fromJson(
      prefs.getString(PREFERENCE_KEY, DEFAULT_PRESETS),
      Array<Preset>::class.java
    )
  }

  /**
   * Returns the preset with given id. Returns `null` if the [Preset] with given id doesn't exist.
   */
  fun get(id: String?): Preset? {
    id ?: return null
    return list().find { it.id == id }
  }

  /**
   * [random] generates a nameless random preset using the provided sound [tag] and [intensity].
   * If sound [tag] is null, full library is considered for randomly selecting sounds for the
   * preset. If it is non-null, only sounds containing the provided tag are considered.
   * [intensity] is a [IntRange] that hints the lower and upper bounds for the number of sounds
   * present in the generated preset. A number is chosen randomly in this range.
   */
  fun random(tag: Sound.Tag?, intensity: IntRange): Preset {
    // TODO: fix
//    val library = Sound.filterLibraryByTag(tag).shuffled()
//    val playerStates = mutableListOf<Preset.PlayerState>()
//    for (i in 0 until Random.nextInt(intensity)) {
//      val volume = 1 + Random.nextInt(0, Player.MAX_VOLUME)
//      val timePeriod = Random.nextInt(Player.MIN_TIME_PERIOD, Player.MAX_TIME_PERIOD + 1)
//      playerStates.add(Preset.PlayerState(library[i], volume, timePeriod))
//    }

    return Preset(UUID.randomUUID().toString(), "", emptyArray())
  }

  /**
   * Updates the given [preset].
   *
   * @throws IllegalArgumentException if [preset] id is not present in the persistent storage.
   */
  fun update(preset: Preset) {
    val presets = list()
    val index = presets.indexOfFirst { it.id == preset.id }
    if (index < 0) {
      throw IllegalArgumentException("cannot update preset with id ${preset.id} as it doesn't exist")
    }

    presets[index] = preset
    commit(presets)
  }

  /**
   * Deletes the preset with given id from the storage.
   *
   * @return `true` on success, `false` on failure.
   */
  fun delete(id: String): Boolean {
    val presets = list().toMutableList()
    val result = presets.removeAll { it.id == id }
    commit(presets.toTypedArray())
    return result
  }

  /**
   * Writes all saved presets present in the storage to the given [OutputStream]. The output format
   * is JSON.
   */
  @Throws(JsonIOException::class)
  fun writeTo(stream: OutputStream) {
    val data = mapOf(
      EXPORT_VERSION_KEY to PREFERENCE_KEY,
      EXPORT_DATA_KEY to prefs.getString(PREFERENCE_KEY, DEFAULT_PRESETS)
    )

    OutputStreamWriter(stream).use { gson.toJson(data, it) }
  }

  /**
   * Reads and saves the presets from an [InputStream] that has the data that was exported using
   * [writeTo]. It overwrites any existing presets in the storage.
   */
  @Throws(JsonIOException::class, JsonSyntaxException::class, IllegalArgumentException::class)
  fun readFrom(stream: InputStream) {
    val data = InputStreamReader(stream).use {
      gson.fromJson(it, hashMapOf<String, String?>()::class.java)
    }

    val version = data?.get(EXPORT_VERSION_KEY)
    version ?: throw IllegalArgumentException("'version' is missing")

    prefs.edit(commit = true) { putString(version, data[EXPORT_DATA_KEY]) }
    migrate()
  }

  private fun commit(presets: Array<Preset>) {
    prefs.edit(commit = true) { putString(PREFERENCE_KEY, gson.toJson(presets)) }
  }

  private fun migrate() {
    migrateToV1()
    // the chain can continue, e.g. migrateToV2().
  }

  /**
   * [migrateToV1] migrates saved user presets from its old definitions to its current
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
  internal fun migrateToV1() {
    val json = prefs.getString(PREF_V0, null)
    if (json.isNullOrBlank()) {
      return
    }

    val presets = mutableListOf<Preset>()
    JSONArray(json).forEach<JSONObject> { preset ->
      val presetName = preset.getString("a")
      val playerStates = mutableListOf<Preset.PlayerState>()

      preset.getJSONArray("b").forEach<JSONObject> { state ->
        playerStates.add(
          Preset.PlayerState(
            soundKey = state.getString("a"),
            // with corrections from the old deserializers
            volume = round(state.getDouble("b") * Player.MAX_VOLUME).roundToInt(),
            timePeriod = state.getInt("c") + Player.MIN_TIME_PERIOD
          )
        )
      }

      val id = UUID.randomUUID().toString()
      presets.add(Preset(id, presetName, playerStates.toTypedArray()))
    }

    prefs.edit(commit = true) {
      remove(PREF_V0)
      putString(PREF_V1, gson.toJson(presets.toTypedArray()))
    }
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
