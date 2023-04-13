package com.github.ashutoshgngwr.noice.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.WritableDownloadIndex
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object ExoPlayerOfflineModule {

  @Provides
  @Singleton
  fun databaseProvider(@ApplicationContext context: Context): DatabaseProvider {
    return StandaloneDatabaseProvider(context)
  }

  @Provides
  @Singleton
  fun cache(@ApplicationContext context: Context, databaseProvider: DatabaseProvider): Cache {
    return SimpleCache(
      File(context.filesDir, "offline-sounds"),
      NoOpCacheEvictor(),
      databaseProvider
    )
  }

  @Provides
  @Singleton
  fun writableDownloadIndex(databaseProvider: DatabaseProvider): WritableDownloadIndex {
    return DefaultDownloadIndex(databaseProvider)
  }

  @Provides
  @Singleton
  fun readableDownloadIndex(downloadIndex: WritableDownloadIndex): DownloadIndex {
    return downloadIndex
  }
}
