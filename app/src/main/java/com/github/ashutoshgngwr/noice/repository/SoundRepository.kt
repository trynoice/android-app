package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.model.SoundDownloadMetadata
import com.github.ashutoshgngwr.noice.model.SoundDownloadState
import com.github.ashutoshgngwr.noice.model.SoundSegment
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SoundNotFoundError
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadIndex
import com.google.gson.Gson
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SoundTag
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
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
  private val downloadIndex: DownloadIndex,
  private val gson: Gson,
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
      manifest.sounds.map { apiSound ->
        val segmentsBasePath = "${manifest.segmentsBasePath}/${apiSound.id}"
        val segments = mutableListOf<SoundSegment>()
        for (apiSegment in apiSound.segments) {
          segments.add(
            SoundSegment(
              name = apiSegment.name,
              isFree = apiSegment.isFree,
              isBridgeSegment = false,
              basePath = "${segmentsBasePath}/${apiSegment.name}",
            )
          )

          if (apiSound.maxSilence > 0) {
            continue
          }

          for (toApiSegment in apiSound.segments) {
            val bridgeName = "${apiSegment.name}_${toApiSegment.name}"
            segments.add(
              SoundSegment(
                name = bridgeName,
                isFree = apiSegment.isFree && toApiSegment.isFree,
                isBridgeSegment = true,
                from = apiSegment.name,
                to = toApiSegment.name,
                basePath = "${segmentsBasePath}/${bridgeName}",
              )
            )
          }
        }

        Sound(
          id = apiSound.id,
          group = groups.getValue(apiSound.groupId),
          name = apiSound.name,
          iconSvg = URLDecoder.decode(
            apiSound.icon.removePrefix("data:image/svg+xml,"),
            StandardCharsets.UTF_8.name(),
          ),
          maxSilence = apiSound.maxSilence,
          isPremium = segments.none { it.isFree },
          segments = segments,
          tags = manifest.tags.filter { apiSound.tags.contains(it.id) },
          sources = apiSound.sources,
        )
      }
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
  fun get(soundId: String): Flow<Resource<Sound>> = list().transform { r ->
    val sound = r.data?.find { it.id == soundId }
    emit(
      when {
        r is Resource.Loading -> Resource.Loading(sound)
        r is Resource.Success && sound != null -> Resource.Success(sound)
        else -> Resource.Failure(r.error ?: SoundNotFoundError, sound)
      }
    )
  }

  /**
   * Returns a [Flow] that emits a list of all [SoundTag]s as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun listTags(): Flow<Resource<List<SoundTag>>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs(TAGS_KEY) },
    loadFromNetwork = { apiClient.cdn().libraryManifest().tags },
    cacheNetworkResult = { cacheStore.put(TAGS_KEY, it) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "listTags:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits a map of CDN paths (relative to `library-manifest.json`) to
   * their md5sums as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun getMd5sums(): Flow<Resource<Map<String, String>>> = fetchNetworkBoundResource(
    loadFromNetwork = { apiClient.cdn().md5sums() },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "getMd5sums:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a flow that actively polls ExoPlayer's [DownloadIndex] and emits a map of sound ids
   * (that are currently downloading or have finished downloading) to their [SoundDownloadState].
   */
  fun getDownloadStates(): Flow<Map<String, SoundDownloadState>> = flow {
    while (true) {
      val downloads = downloadIndex.getDownloads()
      val states = mutableMapOf<String, SoundDownloadState>()
      while (downloads.moveToNext()) {
        val download = downloads.download
        val metadataJson = download.request.data.decodeToString()
        val metadata = gson.fromJson(metadataJson, SoundDownloadMetadata::class.java)
        if (states[metadata.soundId] == SoundDownloadState.DOWNLOADING) {
          continue
        }

        states[metadata.soundId] = when (download.state) {
          Download.STATE_COMPLETED -> SoundDownloadState.DOWNLOADED
          else -> SoundDownloadState.DOWNLOADING
        }
      }

      emit(states)
      downloads.close()
      delay(500L)
    }
  }

  /**
   * Returns a flow that emits `true` if the sound library was updated since the last check.
   */
  fun isLibraryUpdated(): Flow<Resource<Boolean>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      val oldUpdatedAt = cacheStore.getAs<Long>(UPDATED_AT_KEY)
      val newUpdatedAt = apiClient.cdn().libraryManifest().updatedAt.time
      cacheStore.put(UPDATED_AT_KEY, newUpdatedAt)
      oldUpdatedAt != null && oldUpdatedAt < newUpdatedAt
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "isLibraryUpdated:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    }
  )

  companion object {
    private const val LOG_TAG = "SoundRepository"
    private const val SOUND_KEY_PREFIX = "sound"
    private const val TAGS_KEY = "${SOUND_KEY_PREFIX}/tags"
    private const val UPDATED_AT_KEY = "${SOUND_KEY_PREFIX}/updatedAt"
  }
}
