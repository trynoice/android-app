package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SoundNotFoundError
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SoundTag
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
) {

  private val playerManagerState = MutableStateFlow(PlaybackState.STOPPED)
  private val playerStates = MutableStateFlow(emptyArray<PlayerState>())

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
        Sound(
          id = apiSound.id,
          group = groups.getValue(apiSound.groupId),
          name = apiSound.name,
          iconSvg = URLDecoder.decode(
            apiSound.icon.removePrefix("data:image/svg+xml,"),
            StandardCharsets.UTF_8.name(),
          ),
          maxSilence = apiSound.maxSilence,
          segmentsBasePath = "library/${manifest.segmentsBasePath}/${apiSound.id}",
          segments = apiSound.segments,
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
   * Updates and publishes the shared playback state to subscribing clients via [StateFlow]s.
   */
  fun updatePlaybackStates(playerManagerState: PlaybackState, playerStates: Array<PlayerState>) {
    this.playerManagerState.value = playerManagerState
    this.playerStates.value = playerStates
  }

  /**
   * Returns a [Flow] that emits the Player Manager's current [PlaybackState].
   */
  fun getPlayerManagerState(): Flow<PlaybackState> {
    return playerManagerState
  }

  /**
   * Returns a [Flow] that emits an array of [PlayerState]s of all players managed by the Player
   * Manager.
   */
  fun getPlayerStates(): Flow<Array<PlayerState>> {
    return playerStates
  }

  companion object {
    private const val LOG_TAG = "SoundRepository"
    private const val SOUND_KEY_PREFIX = "sound"
    private const val TAGS_KEY = "${SOUND_KEY_PREFIX}/tags"
  }
}
