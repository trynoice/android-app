package com.github.ashutoshgngwr.noice.repository

import com.github.ashutoshgngwr.noice.model.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implements a generic logic that can provide a resource backed by both the cache and the network.
 *
 * @param loadFromCache an optional function to load [Data] from the cache.
 * @param loadFromNetwork a function to load the [Data] from network.
 * @param cacheNetworkResult an optional function to persist [Data] from network to the local cache.
 * @param loadFromNetworkErrorTransform an optional transform that is applied to a [Throwable]
 * thrown by [loadFromNetwork].
 * @return a [Flow]<[Resource]> that emits [Resource.Loading] state with an optional cached value if
 * found. Later, it emits [Resource.Success] or [Resource.Failure] based on the result of
 * [loadFromNetwork]. [Resource.Failure] may contain cached data if it was found.
 */
inline fun <Data : Any> fetchNetworkBoundResource(
  crossinline loadFromCache: suspend () -> Data? = { null },
  crossinline loadFromNetwork: suspend () -> Data,
  crossinline cacheNetworkResult: suspend (Data) -> Unit = { },
  crossinline loadFromNetworkErrorTransform: (Throwable) -> Throwable = { it },
): Flow<Resource<Data>> = flow {
  emit(Resource.Loading())

  val cachedData = loadFromCache.invoke()
  emit(Resource.Loading(cachedData))

  try {
    val data = loadFromNetwork.invoke()
    cacheNetworkResult.invoke(data)
    emit(Resource.Success(data))
  } catch (e: Throwable) {
    emit(Resource.Failure(loadFromNetworkErrorTransform.invoke(e), cachedData))
  }
}
