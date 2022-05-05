package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SoundNotFoundError
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SoundGroup
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements a data access layer for fetching sound related data.
 */
@Singleton
class SoundRepository @Inject constructor(
  private val apiClient: NoiceApiClient,
  private val cacheStore: May,
) {

  /**
   * Returns a [Flow] that emits a list of available [Sound]s as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun list(): Flow<Resource<List<Sound>>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs("${SOUND_KEY_PREFIX}/all") },
    loadFromNetwork = {
      val manifest = apiClient.cdn().libraryManifest()
      val groups = manifest.groups.associateBy { it.id }
      manifest.sounds.map { buildSound(it, groups.getValue(it.groupId), manifest.segmentsBasePath) }
    },
    cacheNetworkResult = { cacheStore.put("${SOUND_KEY_PREFIX}/all", it) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "list:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits the [Sound] with the given [soundId] as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [SoundNotFoundError] if the sound with [soundId] doesn't exist.
   * - [NetworkError] on network errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun get(soundId: String): Flow<Resource<Sound>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs("${SOUND_KEY_PREFIX}/${soundId}") },
    loadFromNetwork = {
      val manifest = apiClient.cdn().libraryManifest()
      val apiSound = manifest.sounds.find { it.id == soundId } ?: throw SoundNotFoundError
      val group = manifest.groups.find { it.id == apiSound.groupId }
      buildSound(apiSound, requireNotNull(group), manifest.segmentsBasePath)
    },
    cacheNetworkResult = { cacheStore.put("${SOUND_KEY_PREFIX}/${soundId}", it) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "get:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  private fun buildSound(
    apiSound: com.trynoice.api.client.models.Sound,
    group: SoundGroup,
    segmentsBasePath: String,
  ): Sound {
    return Sound(
      id = apiSound.id,
      group = group,
      name = apiSound.name,
      iconSvg = URLDecoder.decode(
        apiSound.icon.removePrefix("data:image/svg+xml,"),
        StandardCharsets.UTF_8.name(),
      ),
      maxSilence = apiSound.maxSilence,
      segmentsBasePath = "${segmentsBasePath}/${apiSound.id}",
      segments = apiSound.segments,
      sources = apiSound.sources,
    )
  }

  companion object {
    private const val LOG_TAG = "SoundRepository"
    private const val SOUND_KEY_PREFIX = "sound"
  }
}
