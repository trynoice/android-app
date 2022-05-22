package com.github.ashutoshgngwr.noice.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.ext.keyFlow
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.PresetRepository.Companion.PREFERENCE_KEY
import com.github.ashutoshgngwr.noice.repository.errors.DuplicatePresetError
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

/**
 * [PresetRepository] implements the data access layer for [Preset]. It stores all its data in a
 * shared preference with [PREFERENCE_KEY].
 */
@Singleton
class PresetRepository @Inject constructor(
  @ApplicationContext context: Context,
  private val gson: Gson,
) {

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

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
   * Deletes the preset with given id from the storage.
   *
   * @return `true` if a preset was deleted, `false` otherwise.
   */
  fun delete(id: String) = edit { presets ->
    presets.removeAll { it.id == id }
  }

  /**
   * Lists all [Preset]s present in the persistent storage.
   */
  fun list(): List<Preset> {
    return gson.fromJson(prefs.getString(PREFERENCE_KEY, DEFAULT_PRESETS), PRESET_LIST_TYPE)
  }

  /**
   * Returns a [Flow] that emits an [Array] of all [Preset] whenever the list of saved presets is
   * updated.
   */
  fun listFlow(): Flow<List<Preset>> {
    return prefs.keyFlow(PREFERENCE_KEY).map { list() }
  }

  /**
   * Returns the preset with given id. Returns `null` if the [Preset] with given id doesn't exist.
   */
  fun get(id: String?): Preset? {
    return id?.let { list().firstOrNull { p -> p.id == id } }
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
//    val playerStates = mutableListOf<Preset.PlaybackState>()
//    for (i in 0 until Random.nextInt(intensity)) {
//      val volume = 1 + Random.nextInt(0, Player.MAX_VOLUME)
//      val timePeriod = Random.nextInt(Player.MIN_TIME_PERIOD, Player.MAX_TIME_PERIOD + 1)
//      playerStates.add(Preset.PlaybackState(library[i], volume, timePeriod))
//    }

    return Preset(UUID.randomUUID().toString(), "", emptyArray())
  }

  /**
   * Writes all saved presets present in the storage to the given [OutputStream]. The output format
   * is JSON.
   */
  @Throws(JsonIOException::class)
  fun exportTo(stream: OutputStream) {
    val data = mapOf(
      EXPORT_VERSION_KEY to PREFERENCE_KEY,
      EXPORT_DATA_KEY to prefs.getString(PREFERENCE_KEY, DEFAULT_PRESETS)
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
    prefs.edit { putString(version, data[EXPORT_DATA_KEY]) }
  }

  /**
   * Decodes a preset from a self-contained HTTP URL generated using [writeAsUrl].
   *
   * @return the contained [Preset] if the URL is valid, or `null` [Preset] if the URL is invalid.
   */
  fun readFromUrl(url: String): Preset? {
    val uri = Uri.parse(url)
    val name = uri.getQueryParameter(URI_PARAM_NAME) ?: ""
    val playerStatesJSON = uri.getQueryParameter(URI_PARAM_PLAYER_STATES) ?: return null
    return try {
      val playerStates = gson.fromJson(playerStatesJSON, Array<PlayerState>::class.java)
      Preset(name, playerStates)
    } catch (e: JsonSyntaxException) {
      null
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
      .appendQueryParameter(URI_PARAM_NAME, preset.name)
      .appendQueryParameter(URI_PARAM_PLAYER_STATES, gson.toJson(preset.playerStates))
      .build()
      .toString()
  }

  private fun commit(presets: List<Preset>) {
    prefs.edit { putString(PREFERENCE_KEY, gson.toJson(presets)) }
  }

  private inline fun edit(block: (MutableList<Preset>) -> Unit) {
    synchronized(prefs) {
      val presets: MutableList<Preset> = prefs.getString(PREFERENCE_KEY, DEFAULT_PRESETS)
        .let { gson.fromJson(it, PRESET_LIST_TYPE) }

      block.invoke(presets)
      commit(presets)
    }
  }

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREFERENCE_KEY = "presets.v2"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXPORT_VERSION_KEY = "version"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXPORT_DATA_KEY = "data"

    private val PRESET_LIST_TYPE by lazy {
      TypeToken.getParameterized(List::class.java, Preset::class.java).type
    }

    private const val URI_PARAM_NAME = "n"
    private const val URI_PARAM_PLAYER_STATES = "ps"
    private const val DEFAULT_PRESETS = """[{
      "id": "808feaed-f4ce-4d1e-9179-ae7aec31180e",
      "name": "Thunderstorm by @markwmuller",
      "playerStates": [
        {
          "soundId": "rain",
          "volume": 20
        },
        {
          "soundId": "thunder",
          "volume": 20
        }
      ]
    },
    {
      "id": "13006e01-9413-45d7-bffc-dc577b077d67",
      "name": "Beach by @eMPee584",
      "playerStates": [
        {
          "soundId": "crickets",
          "volume": 6
        },
        {
          "soundId": "seagulls",
          "volume": 6
        },
        {
          "soundId": "seashore",
          "volume": 20
        },
        {
          "soundId": "soft_wind",
          "volume": 6
        },
        {
          "soundId": "wind_through_palm_trees",
          "volume": 15
        }
      ]
    },
    {
      "id": "b76ac285-1265-472c-bcdc-aecba3a28fa2",
      "name": "Camping by @ashutoshgngwr",
      "playerStates": [
        {
          "soundId": "campfire",
          "volume": 22
        },
        {
          "soundId": "night",
          "volume": 6
        },
        {
          "soundId": "quiet_conversations",
          "volume": 5
        },
        {
          "soundId": "soft_wind",
          "volume": 8
        },
        {
          "soundId": "wolves",
          "volume": 3
        }
      ]
    }]"""
  }
}
