package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.preference.PreferenceManager
import androidx.room.withTransaction
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.data.models.DefaultPresetsSyncVersionDto
import com.github.ashutoshgngwr.noice.di.AppCoroutineScope
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.models.PresetV0
import com.github.ashutoshgngwr.noice.models.PresetV1
import com.github.ashutoshgngwr.noice.models.PresetV2
import com.github.ashutoshgngwr.noice.models.PresetsExport
import com.github.ashutoshgngwr.noice.models.PresetsExportV0
import com.github.ashutoshgngwr.noice.models.PresetsExportV1
import com.github.ashutoshgngwr.noice.models.SoundTag
import com.github.ashutoshgngwr.noice.models.toDomainEntity
import com.github.ashutoshgngwr.noice.models.toRoomDto
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.text.DateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PresetRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val appDb: AppDatabase,
  private val soundRepository: SoundRepository,
  private val gson: Gson,
  @AppCoroutineScope appScope: CoroutineScope,
  private val appDispatchers: AppDispatchers,
) {

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  init {
    appScope.launch(appDispatchers.io) { migrate() }
  }

  /**
   * Saves the given preset. It overwrites the existing preset with the same id if it exists.
   *
   * @throws IllegalArgumentException if [Preset.id] is missing.
   */
  suspend fun save(preset: Preset) {
    require(preset.id.isNotBlank()) { "preset must contain a valid ID" }
    appDb.presets().save(preset.toRoomDto(gson))
  }

  /**
   * Deletes the preset with given id. No-op if such a preset doesn't exist.
   */
  suspend fun delete(id: String) {
    appDb.presets().deleteById(id)
  }

  /**
   * @returns a paging data flow to page all saved presets.
   */
  fun pagingDataFlow(nameFilter: String = ""): Flow<PagingData<Preset>> {
    return Pager(PagingConfig(pageSize = 20)) { appDb.presets().pagingSource("%${nameFilter}%") }
      .flow
      .map { pagingData -> pagingData.map { it.toDomainEntity(gson) } }
  }

  /**
   * @return the preset with given id. Returns `null` if the [Preset] with given id doesn't exist.
   */
  suspend fun get(id: String): Preset? {
    return appDb.presets()
      .getById(id)
      ?.toDomainEntity(gson)
  }

  /**
   * @return a [Flow] that emits the first preset that only contains the given [soundStates].
   */
  fun getBySoundStatesFlow(soundStates: SortedMap<String, Float>): Flow<Preset?> {
    return appDb.presets()
      .getBySoundStatesJsonFlow(gson.toJson(soundStates))
      .map { it?.toDomainEntity(gson) }
  }

  /**
   * @return a [Preset] randomly picked from the saved presets or `null` if there are no saved
   * presets.
   */
  suspend fun getRandom(): Preset? {
    return appDb.presets()
      .getRandom()
      ?.toDomainEntity(gson)
  }

  /**
   * @return the preset that falls next to the [currentPreset] when all saved presets are ordered by
   * their name. If the [currentPreset] is the last one in succession, it returns the first preset.
   */
  suspend fun getNextPreset(currentPreset: Preset): Preset? {
    return (appDb.presets()
      .getNextOrderedByName(currentPreset.name)
      ?: appDb.presets().getFirstOrderedByName())
      ?.toDomainEntity(gson)
  }

  /**
   * @return the preset that falls just before the [currentPreset] when all saved presets are
   * ordered by their name. If the [currentPreset] is the first one in succession, it returns the
   * last preset.
   */
  suspend fun getPreviousPreset(currentPreset: Preset): Preset? {
    return (appDb.presets()
      .getPreviousOrderedByName(currentPreset.name)
      ?: appDb.presets().getLastOrderedByName())
      ?.toDomainEntity(gson)
  }

  /**
   * @return `true` if a preset with the given [name] already exists.
   */
  suspend fun existsByName(name: String): Boolean {
    return appDb.presets().countByName(name) > 0
  }

  /**
   * Returns a [Flow] that emits a generated preset as a [Resource] based on given [tags] and
   * [soundCount].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   *
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
  suspend fun exportTo(stream: OutputStream): Unit = withContext(appDispatchers.io) {
    val export = appDb.presets()
      .list()
      .map { it.toDomainEntity(gson) }
      .let { presets ->
        PresetsExportV1(
          presets = presets,
          exportedAt = DateFormat.getDateTimeInstance().format(Date()),
        )
      }

    val gson = gson.newBuilder().setPrettyPrinting().create()
    OutputStreamWriter(stream).use { gson.toJson(export, it) }
  }

  /**
   * Reads and saves the presets from an [InputStream] that has the data that was exported using
   * [exportTo]. It overwrites any existing presets in the storage.
   */
  @Throws(JsonIOException::class, JsonSyntaxException::class, IllegalArgumentException::class)
  suspend fun importFrom(stream: InputStream): Unit = withContext(appDispatchers.io) {
    val export = InputStreamReader(stream).use {
      gson.newBuilder()
        .registerTypeAdapter(PresetsExport::class.java, PresetsExportDeserializer())
        .create()
        .fromJson(it, PresetsExport::class.java)
    }

    appDb.withTransaction {
      appDb.presets().deleteAll()
      when (export) {
        is PresetsExportV0 -> {
          prefs.edit {
            clear()
            putString(export.version, export.data)
          }

          migrate()
        }

        is PresetsExportV1 -> appDb.withTransaction {
          export.presets.forEach { save(it) }
        }
      }
    }
  }

  /**
   * Decodes a preset from a self-contained HTTP URL generated using [writeAsUrl].
   *
   * @return the contained [Preset] if the URL is valid, or `null` [Preset] if the URL is invalid.
   */
  fun readFromUrl(url: String): Preset? {
    val uri = Uri.parse(url)
    val version = uri.getQueryParameter(URI_PARAM_VERSION) ?: URI_VERSION_0
    val name = uri.getQueryParameter(URI_PARAM_NAME) ?: ""

    when (version) {
      URI_VERSION_0 -> {
        val playerStatesJson = uri.getQueryParameter(URI_PARAM_PLAYER_STATES) ?: return null
        return try {
          PresetV2(name, gson.fromJson(playerStatesJson, Array<PresetV2.PlayerState>::class.java))
            .toPresetV3()
        } catch (e: JsonSyntaxException) {
          null
        }
      }

      URI_VERSION_1 -> {
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
      .appendQueryParameter(URI_PARAM_VERSION, URI_VERSION_1)
      .appendQueryParameter(URI_PARAM_NAME, preset.name)
      .appendQueryParameter(URI_PARAM_SOUND_STATES, gson.toJson(preset.soundStates))
      .build()
      .toString()
  }

  private suspend fun migrate() {
    migrateToV1()
    migrateToV2()
    migrateToV3()
    migrateDefaultPresets()
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
  private fun migrateToV1() {
    val presetsV0 = prefs.getString(PRESETS_V0_KEY, "[]")
      .let { gson.fromJson(it, Array<PresetV0>::class.java) }

    if (presetsV0.isEmpty()) {
      return
    }

    val presetsV1 = prefs.getString(PRESETS_V1_KEY, "[]")
      .let { gson.fromJson(it, Array<PresetV1>::class.java) }
      .toMutableList()

    presetsV0.forEach { presetV0 ->
      presetsV1.add(presetV0.toPresetV1())
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
  private fun migrateToV2() {
    val presetsV1 = prefs.getString(PRESETS_V1_KEY, "[]")
      .let { gson.fromJson(it, Array<PresetV1>::class.java) }

    if (presetsV1.isEmpty()) {
      return
    }

    val presetsV2 = prefs.getString(PRESETS_V2_KEY, "[]")
      .let { gson.fromJson(it, Array<PresetV2>::class.java) }
      .toMutableList()

    presetsV1.forEach { presetV1 ->
      val presetV2 = presetV1.toPresetV2()
      // only append to existing presets if another preset with same player states doesn't exist.
      if (presetsV2.none { it.playerStates.contentEquals(presetV2.playerStates) }) {
        presetsV2.add(presetV2)
      }
    }

    prefs.edit {
      remove(PRESETS_V1_KEY)
      putString(PRESETS_V2_KEY, gson.toJson(presetsV2))
    }
  }

  /**
   * Migrates V2 presets to V3 presets to adapt for the following changes:
   *  - V3 Presets have a simpler model. They use a map of sound ids to their volumes to represent
   *    sounds states.
   *  - V2 presets are stored in the default shared preferences. V3 presets use Room.
   */
  private suspend fun migrateToV3() = appDb.withTransaction {
    if (appDb.presets().getDefaultPresetsSyncedVersion() == null) {
      appDb.presets()
        .saveDefaultPresetsSyncVersion(
          DefaultPresetsSyncVersionDto(
            prefs.getInt(
              "defaultPresetsSyncVersion",
              if (prefs.contains(PRESETS_V2_KEY)) 0 else -1,
            )
          )
        )
    }

    prefs.getString(PRESETS_V2_KEY, "[]")
      .let { gson.fromJson(it, Array<PresetV2>::class.java) }
      .map { it.toPresetV3() }
      .forEach { appDb.presets().save(it.toRoomDto(gson)) }

    prefs.edit {
      remove("defaultPresetsSyncVersion")
      remove(PRESETS_V2_KEY)
    }
  }

  private suspend fun migrateDefaultPresets() = appDb.withTransaction {
    val syncFrom = 1 + (appDb.presets().getDefaultPresetsSyncedVersion() ?: -1)

    DEFAULT_PRESETS.subList(syncFrom, DEFAULT_PRESETS.size)
      .flatMap { gson.fromJson(it, Array<Preset>::class.java).toList() }
      .forEach { save(it) }

    appDb.presets()
      .saveDefaultPresetsSyncVersion(DefaultPresetsSyncVersionDto(DEFAULT_PRESETS.size - 1))
  }

  companion object {
    private const val PRESETS_V0_KEY = "presets"
    private const val PRESETS_V1_KEY = "presets.v1"
    private const val PRESETS_V2_KEY = "presets.v2"

    private const val URI_PARAM_VERSION = "v"
    private const val URI_PARAM_NAME = "n"
    private const val URI_PARAM_PLAYER_STATES = "ps"
    private const val URI_PARAM_SOUND_STATES = "s"

    private const val URI_VERSION_0 = "0"
    private const val URI_VERSION_1 = "1"

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

  private class PresetsExportDeserializer : JsonDeserializer<PresetsExport> {

    override fun deserialize(
      json: JsonElement,
      typeOfT: Type?,
      context: JsonDeserializationContext
    ): PresetsExport {
      return when (val version = json.asJsonObject?.get("version")?.asString) {
        PresetsExportV1.VERSION_STRING -> context.deserialize(json, PresetsExportV1::class.java)
        PRESETS_V0_KEY,
        PRESETS_V1_KEY,
        PRESETS_V2_KEY -> context.deserialize(json, PresetsExportV0::class.java)
        else -> throw JsonParseException("unknown preset export version: $version")
      }
    }
  }
}
