package com.github.ashutoshgngwr.noice.repository

import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.data.models.LibraryUpdateTimeDto
import com.github.ashutoshgngwr.noice.data.models.SoundMetadataDto
import com.github.ashutoshgngwr.noice.data.models.SoundSegmentDto
import com.github.ashutoshgngwr.noice.data.models.SoundSourceDto
import com.github.ashutoshgngwr.noice.data.models.SoundTagCrossRef
import com.github.ashutoshgngwr.noice.models.Sound
import com.github.ashutoshgngwr.noice.models.SoundDownloadMetadata
import com.github.ashutoshgngwr.noice.models.SoundDownloadState
import com.github.ashutoshgngwr.noice.models.SoundInfo
import com.github.ashutoshgngwr.noice.models.SoundTag
import com.github.ashutoshgngwr.noice.models.toDomainEntity
import com.github.ashutoshgngwr.noice.models.toRoomDto
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SoundNotFoundError
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadIndex
import com.google.gson.Gson
import com.trynoice.api.client.NoiceApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements a data access layer for fetching sound related data.
 */
@Singleton
class SoundRepository @Inject constructor(
  private val apiClient: NoiceApiClient,
  private val appDb: AppDatabase,
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
  fun listInfo(): Flow<Resource<List<SoundInfo>>> = fetchNetworkBoundResource(
    loadFromCache = { appDb.sounds().listInfo().toDomainEntity() },
    loadFromNetwork = {
      loadLibraryManifestInCacheStore()
      appDb.sounds().listInfo().toDomainEntity()
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "listInfo:", e)
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
    loadFromCache = { appDb.sounds().get(soundId)?.toDomainEntity() },
    loadFromNetwork = {
      loadLibraryManifestInCacheStore()
      appDb.sounds()
        .get(soundId)
        ?.toDomainEntity()
        ?: throw SoundNotFoundError
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "get:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits the count of premium [Sound]s in the current library.
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun countPremium(): Flow<Resource<Int>> = fetchNetworkBoundResource(
    loadFromCache = { appDb.sounds().countPremium() },
    loadFromNetwork = {
      loadLibraryManifestInCacheStore()
      appDb.sounds().countPremium()
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "countPremium:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

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
    loadFromCache = { appDb.sounds().listTags().toDomainEntity() },
    loadFromNetwork = {
      loadLibraryManifestInCacheStore()
      appDb.sounds().listTags().toDomainEntity()
    },
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
      delay(1000L)
    }
  }

  /**
   * Returns a flow that emits `true` if the sound library was updated since the last check.
   */
  fun isLibraryUpdated(): Flow<Resource<Boolean>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      val oldUpdatedAt = appDb.sounds().getLibraryUpdateTime()
      val newUpdatedAt = apiClient.cdn().libraryManifest().updatedAt
      appDb.sounds().saveLibraryUpdateTime(LibraryUpdateTimeDto(newUpdatedAt))
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

  private suspend fun loadLibraryManifestInCacheStore(): Unit = appDb.withTransaction {
    val manifest = apiClient.cdn().libraryManifest()
    val groups = manifest.groups.associate { it.id to it.toRoomDto() }
    val tags = manifest.tags.toRoomDto()
    appDb.sounds().saveGroups(groups.values.toList())
    appDb.sounds().saveTags(tags)

    manifest.sounds.forEach { apiSound ->
      appDb.sounds().saveMetadata(
        SoundMetadataDto(
          id = apiSound.id,
          groupId = apiSound.groupId,
          name = apiSound.name,
          iconSvg = Uri.decode(apiSound.icon.removePrefix("data:image/svg+xml,")),
          maxSilence = apiSound.maxSilence,
          isPremium = apiSound.segments.none { it.isFree },
          hasPremiumSegments = apiSound.segments.any { !it.isFree }
        )
      )

      val segmentsBasePath = "${manifest.segmentsBasePath}/${apiSound.id}"
      for (apiSegment in apiSound.segments) {
        appDb.sounds().saveSegment(
          SoundSegmentDto(
            soundId = apiSound.id,
            name = apiSegment.name,
            basePath = "${segmentsBasePath}/${apiSegment.name}",
            isFree = apiSegment.isFree,
            isBridgeSegment = false,
            from = null,
            to = null,
          )
        )

        if (apiSound.maxSilence > 0) {
          continue
        }

        for (toApiSegment in apiSound.segments) {
          val bridgeName = "${apiSegment.name}_${toApiSegment.name}"
          appDb.sounds().saveSegment(
            SoundSegmentDto(
              soundId = apiSound.id,
              name = bridgeName,
              isFree = apiSegment.isFree && toApiSegment.isFree,
              isBridgeSegment = true,
              from = apiSegment.name,
              to = toApiSegment.name,
              basePath = "${segmentsBasePath}/${bridgeName}",
            )
          )
        }

        apiSound.tags.map { SoundTagCrossRef(apiSound.id, it) }
          .also { appDb.sounds().saveSoundTagCrossRefs(it) }

        appDb.sounds().saveSources(
          apiSound.sources.map { apiSource ->
            SoundSourceDto(
              soundId = apiSound.id,
              name = apiSource.name,
              url = apiSource.url,
              license = apiSource.license,
              authorName = apiSource.author?.name,
              authorUrl = apiSource.author?.url,
            )
          }
        )
      }
    }
  }

  companion object {
    private const val LOG_TAG = "SoundRepository"
  }
}
