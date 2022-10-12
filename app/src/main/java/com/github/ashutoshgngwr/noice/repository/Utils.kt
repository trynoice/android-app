package com.github.ashutoshgngwr.noice.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A wrapper class for holding [Resource.Loading], [Resource.Success] or [Resource.Failure] state of
 * an operation along with its result [data] and [error].
 */
sealed class Resource<T : Any> private constructor(val data: T?, val error: Throwable?) {

  /**
   * Indicates that the operation is processing.
   *
   * @param data data that the running operation has been able to load so far. It may be `null`.
   */
  class Loading<T : Any>(data: T? = null) : Resource<T>(data, null)

  /**
   * Indicates that the operation has succeeded.
   *
   * @param data must not be `null`.
   */
  class Success<T : Any>(data: T) : Resource<T>(data, null)

  /**
   * Indicates that the operation has failed.
   *
   * @param error must not be `null`.
   * @param data any data that was returned by the failing operation. It may be `null`
   */
  class Failure<T : Any>(error: Throwable, data: T? = null) : Resource<T>(data, error)
}

/**
 * Implements a generic logic that can provide a resource backed by both the cache and the network.
 *
 * @param loadFromCache an optional function to load [Data] from the cache.
 * @param loadFromNetwork a function to load the [Data] from network.
 * @param cacheNetworkResult an optional function to persist [Data] from network to the local cache.
 * @param loadFromNetworkErrorTransform an optional transform applied to a [Throwable] thrown by
 * [loadFromNetwork] before emitting it with [Resource.Failure].
 * @return a [Flow]<[Resource]> that emits [Resource.Loading] state with an optional cached value if
 * found. Later, it emits [Resource.Success] or [Resource.Failure] based on the result of
 * [loadFromNetwork]. [Resource.Failure] may contain cached data if it was found.
 */
fun <Data : Any> fetchNetworkBoundResource(
  loadFromCache: suspend () -> Data? = { null },
  loadFromNetwork: suspend () -> Data,
  cacheNetworkResult: suspend (Data) -> Unit = { },
  loadFromNetworkErrorTransform: (Throwable) -> Throwable = { it },
): Flow<Resource<Data>> = flow {
  var cachedData: Data? = null
  try {
    cachedData = loadFromCache.invoke()
    emit(Resource.Loading(cachedData))
    val data = loadFromNetwork.invoke()
    cacheNetworkResult.invoke(data)
    emit(Resource.Success(data))
  } catch (e: Throwable) {
    emit(Resource.Failure(loadFromNetworkErrorTransform.invoke(e), cachedData))
  }
}
