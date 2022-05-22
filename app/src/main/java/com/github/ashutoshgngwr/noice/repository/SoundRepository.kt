package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SoundNotFoundError
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SoundGroup
import com.trynoice.api.client.models.SoundTag
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        val tags = manifest.tags.filter { apiSound.tags.contains(it.id) }
        buildSound(apiSound, groups.getValue(apiSound.groupId), tags, manifest.segmentsBasePath)
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
  fun get(soundId: String): Flow<Resource<Sound>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs("${SOUND_KEY_PREFIX}/${soundId}") },
    loadFromNetwork = {
      val manifest = apiClient.cdn().libraryManifest()
      val apiSound = manifest.sounds.find { it.id == soundId } ?: throw SoundNotFoundError
      val group = manifest.groups.find { it.id == apiSound.groupId }
      val tags = manifest.tags.filter { apiSound.tags.contains(it.id) }
      buildSound(apiSound, requireNotNull(group), tags, manifest.segmentsBasePath)
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
    tags: List<SoundTag>,
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
      segmentsBasePath = "library/${segmentsBasePath}/${apiSound.id}",
      segments = apiSound.segments,
      tags = tags,
      sources = apiSound.sources,
    )
  }

  /**
   * Updates and publishes the shared playback state to subscribing clients via [StateFlow]s.
   */
  fun updatePlaybackStates(playerManagerState: PlaybackState, playerStates: Array<PlayerState>) {
    this.playerManagerState.value = playerManagerState
    this.playerStates.value = playerStates
  }

  /**
   * Returns a [StateFlow] that emits the Player Manager's current [PlaybackState].
   */
  fun getPlayerManagerState(): StateFlow<PlaybackState> {
    return playerManagerState
  }

  /**
   * Returns a [StateFlow] that emits an array of [PlayerState]s of all players managed by the
   * Player Manager.
   */
  fun getPlayerStates(): StateFlow<Array<PlayerState>> {
    return playerStates
  }

  companion object {
    private const val LOG_TAG = "SoundRepository"
    private const val SOUND_KEY_PREFIX = "sound"
  }
}
