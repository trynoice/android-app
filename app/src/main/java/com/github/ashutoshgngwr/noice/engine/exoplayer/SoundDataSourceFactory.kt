package com.github.ashutoshgngwr.noice.engine.exoplayer

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.trynoice.api.client.NoiceApiClient

/**
 * An ExoPlayer factory that uses [CdnSoundDataSource.Factory] when [enableDownloadedSounds] is
 * `false`. It uses a [CacheDataSource.Factory] with [CdnSoundDataSource.Factory] upstream
 * otherwise.
 */
@OptIn(UnstableApi::class)
class SoundDataSourceFactory(apiClient: NoiceApiClient, downloadCache: Cache) : DataSource.Factory {

  var enableDownloadedSounds: Boolean = false

  private val cdnDataSourceFactory: DataSource.Factory = CdnSoundDataSource.Factory(apiClient)
  private val offlineDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
    .setCache(downloadCache)
    .setUpstreamDataSourceFactory(cdnDataSourceFactory)
    .setCacheWriteDataSinkFactory(null)

  override fun createDataSource(): DataSource {
    return if (enableDownloadedSounds) {
      offlineDataSourceFactory.createDataSource()
    } else {
      cdnDataSourceFactory.createDataSource()
    }
  }
}
