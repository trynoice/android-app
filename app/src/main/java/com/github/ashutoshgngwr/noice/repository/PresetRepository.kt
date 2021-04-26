package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.repository.PresetRepository.Companion.PREFERENCE_KEY
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.google.gson.GsonBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.round

/**
 * [PresetRepository] implements the data access layer for [Preset]. It stores all its data in a
 * shared preference with [PREFERENCE_KEY].
 */
class PresetRepository private constructor(context: Context) {

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_V0 = "presets"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_V1 = "presets.v1"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREFERENCE_KEY = PREF_V1

    /**
     * Creates a new instance of [PresetRepository]. Needed because mockk is unable to mock
     * constructors on Android instrumented test and there's no cleaner way to inject mocks in
     * Android components than mocking companion object methods.
     */
    fun newInstance(context: Context) = PresetRepository(context)
  }

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
  private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

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
    return gson.fromJson(prefs.getString(PREFERENCE_KEY, "[]"), Array<Preset>::class.java)
  }

  /**
   * Returns the preset with given id. Returns `null` if the [Preset] with given id doesn't exist.
   */
  fun get(id: String?): Preset? {
    id ?: return null
    return list().find { it.id == id }
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
            volume = round(state.getDouble("b") * Player.MAX_VOLUME).toInt(),
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
