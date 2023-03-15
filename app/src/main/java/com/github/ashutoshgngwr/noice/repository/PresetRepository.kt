package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.ext.keyFlow
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.models.PresetV0
import com.github.ashutoshgngwr.noice.models.PresetV1
import com.github.ashutoshgngwr.noice.models.PresetV2
import com.github.ashutoshgngwr.noice.models.SoundTag
import com.github.ashutoshgngwr.noice.repository.errors.DuplicatePresetError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.PresetNotFoundError
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * [PresetRepository] implements the data access layer for [Preset]. It stores all its data in a
 * shared preference with [PresetRepository.PRESETS_KEY].
 */
@Singleton
class PresetRepository @Inject constructor(
  @ApplicationContext context: Context,
  private val soundRepository: SoundRepository,
  private val gson: Gson,
) {

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  init {
    val syncFrom = 1 + prefs.getInt(
      DEFAULT_PRESETS_SYNC_VERSION_KEY,
      if (prefs.contains(PRESETS_KEY)) 0 else -1,
    )

    DEFAULT_PRESETS.subList(syncFrom, DEFAULT_PRESETS.size).forEach { defaultPresets ->
      edit { presets ->
        gson.fromJson<List<Preset>>(defaultPresets, PRESET_LIST_TYPE)
          .also { presets.addAll(it) }
      }
    }

    prefs.edit { putInt(DEFAULT_PRESETS_SYNC_VERSION_KEY, DEFAULT_PRESETS.size - 1) }
    migrate()
  }

  /**
   * Adds the given preset to persistent storage.
   *
   * @throws IllegalArgumentException if [Preset.id] is missing.
   * @throws DuplicatePresetError if a preset with given id already exists.
   */
  fun create(preset: Preset) = edit { presets ->
    require(preset.id.isNotBlank()) { "preset must contain a valid ID" }
    if (presets.any { it.id == preset.id }) {
      throw DuplicatePresetError
    }

    presets.add(preset)
  }

  /**
   * Updates the given [preset].
   *
   * @throws PresetNotFoundError if [preset] id is not present in the persistent storage.
   */
  fun update(preset: Preset) = edit { presets ->
    val index = presets.indexOfFirst { it.id == preset.id }
    if (index < 0) {
      throw PresetNotFoundError
    }

    presets[index] = preset
  }

  /**
   * Deletes the preset with given id from the storage. No-op if such a preset doesn't exist.
   */
  fun delete(id: String) = edit { presets ->
    presets.removeAll { it.id == id }
  }

  /**
   * Lists all [Preset]s present in the persistent storage.
   */
  fun list(): List<Preset> {
    return gson.fromJson(prefs.getString(PRESETS_KEY, "[]"), PRESET_LIST_TYPE)
  }

  /**
   * Returns a [Flow] that emits an [Array] of all [Preset] whenever the list of saved presets is
   * updated.
   */
  fun listFlow(): Flow<List<Preset>> {
    return prefs.keyFlow(PRESETS_KEY).map { list() }
  }

  /**
   * Returns the preset with given id. Returns `null` if the [Preset] with given id doesn't exist.
   */
  fun get(id: String?): Preset? {
    return id?.let { list().firstOrNull { p -> p.id == id } }
  }

  /**
   * Returns a [Flow] that emits a generated preset based on given [tags] and [soundCount].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun generate(tags: Set<SoundTag>, soundCount: Int): Flow<Resource<Preset>> {
    return soundRepository.listInfo()
      .map { r ->
        when {
          r is Resource.Loading -> Resource.Loading(null)
          r.data?.isNotEmpty() == true -> {
            r.data.sortedByDescending { it.tags.intersect(tags).size }
              .take(soundCount * 2)
              .shuffled()
              .take(soundCount)
              .associate { it.id to (0.25F + Random.nextFloat() * 0.75F) }
              .let { Preset("", it.toSortedMap()) }
              .let { Resource.Success(it) }
          }
          else -> Resource.Failure(r.error ?: Exception())
        }
      }
  }

  /**
   * Writes all saved presets present in the storage to the given [OutputStream]. The output format
   * is JSON.
   */
  @Throws(JsonIOException::class)
  fun exportTo(stream: OutputStream) {
    val data = mapOf(
      EXPORT_VERSION_KEY to PRESETS_KEY,
      EXPORT_DATA_KEY to prefs.getString(PRESETS_KEY, "[]")
    )

    OutputStreamWriter(stream).use { gson.toJson(data, it) }
  }

  /**
   * Reads and saves the presets from an [InputStream] that has the data that was exported using
   * [exportTo]. It overwrites any existing presets in the storage.
   */
  @Throws(JsonIOException::class, JsonSyntaxException::class, IllegalArgumentException::class)
  fun importFrom(stream: InputStream) {
    val data = InputStreamReader(stream).use {
      gson.fromJson(it, hashMapOf<String, String?>()::class.java)
    }

    val version = data?.get(EXPORT_VERSION_KEY)
    version ?: throw IllegalArgumentException("'version' is missing")
    prefs.edit {
      clear()
      putString(version, data[EXPORT_DATA_KEY])
    }

    migrate()
  }

  /**
   * Decodes a preset from a self-contained HTTP URL generated using [writeAsUrl].
   *
   * @return the contained [Preset] if the URL is valid, or `null` [Preset] if the URL is invalid.
   */
  fun readFromUrl(url: String): Preset? {
    val uri = Uri.parse(url)
    val version = uri.getQueryParameter(URI_PARAM_VERSION) ?: PRESETS_V2_KEY
    val name = uri.getQueryParameter(URI_PARAM_NAME) ?: ""

    when (version) {
      PRESETS_V2_KEY -> {
        val playerStatesJson = uri.getQueryParameter(URI_PARAM_PLAYER_STATES) ?: return null
        return try {
          PresetV2(name, gson.fromJson(playerStatesJson, PresetV2.GSON_TYPE_PLAYER_STATES))
            .toPresetV3()
        } catch (e: JsonSyntaxException) {
          null
        }
      }

      PRESETS_V3_KEY -> {
        val soundStatesJson = uri.getQueryParameter(URI_PARAM_SOUND_STATES) ?: return null
        return try {
          Preset(name, gson.fromJson(soundStatesJson, Preset.GSON_TYPE_SOUND_STATES))
        } catch (e: JsonSyntaxException) {
          null
        }
      }

      else -> return null
    }
  }

  /**
   * Encodes the preset to a self-contained HTTP URL.
   */
  fun writeAsUrl(preset: Preset): String {
    return Uri.Builder()
      .scheme("https")
      .authority("trynoice.com")
      .path("/preset")
      .appendQueryParameter(URI_PARAM_VERSION, PRESETS_KEY)
      .appendQueryParameter(URI_PARAM_NAME, preset.name)
      .appendQueryParameter(URI_PARAM_SOUND_STATES, gson.toJson(preset.soundStates))
      .build()
      .toString()
  }

  private fun commit(presets: List<Preset>) {
    prefs.edit { putString(PRESETS_KEY, gson.toJson(presets)) }
  }

  private inline fun edit(block: (MutableList<Preset>) -> Unit) {
    synchronized(prefs) {
      val presets: MutableList<Preset> = prefs.getString(PRESETS_KEY, "[]")
        .let { gson.fromJson(it, PRESET_LIST_TYPE) }

      block.invoke(presets)
      presets.sortBy { it.name.lowercase() }
      commit(presets)
    }
  }

  private fun migrate() {
    migrateToV1()
    migrateToV2()
    migrateToV3()
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
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun migrateToV1() = synchronized(prefs) {
    val presetsV0 = prefs.getString(PRESETS_V0_KEY, "[]")
      .let { gson.fromJson<List<PresetV0>>(it, GSON_TYPE_V0) }
    if (presetsV0.isEmpty()) {
      return
    }

    val presetsV1 = prefs.getString(PRESETS_V1_KEY, "[]")
      .let { gson.fromJson<MutableList<PresetV1>>(it, GSON_TYPE_V1) }
    presetsV0.forEach { presetV0 ->
      val presetV1 = presetV0.toPresetV1()
      // only append to existing presets if another preset with same player states doesn't exist.
      if (presetsV1.none { it.playerStates == presetV1.playerStates }) {
        presetsV1.add(presetV1.copy(name = "${presetV1.name} (v0)"))
      }
    }

    prefs.edit {
      remove(PRESETS_V0_KEY)
      putString(PRESETS_V1_KEY, gson.toJson(presetsV1))
    }
  }

  /**
   * Migrates V1 presets to V2 presets to adapt for the following changes:
   *  - V2 Presets don't understand `timePeriod` field from [PresetV1.PlayerState].
   *  - V2 sound library use `soundId` field instead of `soundKey`.
   */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun migrateToV2() = synchronized(prefs) {
    val presetsV1 = prefs.getString(PRESETS_V1_KEY, "[]")
      .let { gson.fromJson<List<PresetV1>>(it, GSON_TYPE_V1) }
    if (presetsV1.isEmpty()) {
      return
    }

    val presetsV2 = prefs.getString(PRESETS_V2_KEY, "[]")
      .let { gson.fromJson<MutableList<PresetV2>>(it, GSON_TYPE_V2) }
    presetsV1.forEach { presetV1 ->
      val presetV2 = presetV1.toPresetV2()
      // only append to existing presets if another preset with same player states doesn't exist.
      if (presetsV2.none { it.playerStates == presetV2.playerStates }) {
        presetsV2.add(presetV2.copy(name = "${presetV2.name} (v1)"))
      }
    }

    prefs.edit {
      remove(PRESETS_V1_KEY)
      putString(PRESETS_V2_KEY, gson.toJson(presetsV2))
    }
  }

  @VisibleForTesting
  internal fun migrateToV3() = synchronized(prefs) {
    val presetsV2 = prefs.getString(PRESETS_V2_KEY, "[]")
      .let { gson.fromJson<List<PresetV2>>(it, GSON_TYPE_V2) }
    if (presetsV2.isEmpty()) {
      return
    }

    val presetsV3 = prefs.getString(PRESETS_V3_KEY, "[]")
      .let { gson.fromJson<MutableList<Preset>>(it, GSON_TYPE_V3) }

    presetsV2.forEach { presetsV3.add(it.toPresetV3()) }
    prefs.edit {
      remove(PRESETS_V2_KEY)
      putString(PRESETS_V3_KEY, gson.toJson(presetsV3))
    }
  }

  companion object {
    private const val PRESETS_V0_KEY = "presets"
    private const val PRESETS_V1_KEY = "presets.v1"
    private const val PRESETS_V2_KEY = "presets.v2"
    private const val PRESETS_V3_KEY = "presets.v3"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PRESETS_KEY = PRESETS_V3_KEY

    private const val DEFAULT_PRESETS_SYNC_VERSION_KEY = "defaultPresetsSyncVersion"

    private val GSON_TYPE_V0 = TypeToken.getParameterized(List::class.java, PresetV0::class.java)
      .type

    private val GSON_TYPE_V1 = TypeToken.getParameterized(List::class.java, PresetV1::class.java)
      .type

    private val GSON_TYPE_V2 = TypeToken.getParameterized(List::class.java, PresetV2::class.java)
      .type

    private val GSON_TYPE_V3 = TypeToken.getParameterized(List::class.java, Preset::class.java)
      .type

    private val PRESET_LIST_TYPE = GSON_TYPE_V3

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXPORT_VERSION_KEY = "version"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXPORT_DATA_KEY = "data"

    private const val URI_PARAM_VERSION = "v"
    private const val URI_PARAM_NAME = "n"
    private const val URI_PARAM_PLAYER_STATES = "ps"
    private const val URI_PARAM_SOUND_STATES = "s"

    /**
     * A versioned map of default presets such that presets added in later versions can be added to
     * an existing user's saved presets without recreating the ones that user had already deleted.
     */
    private val DEFAULT_PRESETS = listOf(
      """[
        {
          "id": "808feaed-f4ce-4d1e-9179-ae7aec31180e",
          "name": "Thunderstorm by @markwmuller",
          "soundStates": {
            "rain": 0.8,
            "thunder": 0.8
          }
        },
        {
          "id": "13006e01-9413-45d7-bffc-dc577b077d67",
          "name": "Beach by @eMPee584",
          "soundStates": {
            "crickets": 0.24,
            "seagulls": 0.24,
            "seashore": 0.8,
            "soft_wind": 0.24,
            "wind_through_palm_trees": 0.6
          }
        },
        {
          "id": "b76ac285-1265-472c-bcdc-aecba3a28fa2",
          "name": "Camping by @ashutoshgngwr",
          "soundStates": {
            "campfire": 0.88,
            "night": 0.24,
            "quiet_conversations": 0.2,
            "soft_wind": 0.32,
            "wolves": 0.12
          }
        }
      ]""",
      """[
        {
          "id": "b6eb323d-e146-4690-a1a8-b8d6802001b3",
          "name": "Womb Simulator by @lrq3000",
          "soundStates": {
            "brownian_noise": 1.0,
            "rain": 1.0,
            "seashore": 0.48,
            "soft_wind": 1.0,
            "train": 0.72,
            "water_stream": 0.32,
            "wind_through_palm_trees": 0.52
          }
        }
      ]""",
    )
  }
}
