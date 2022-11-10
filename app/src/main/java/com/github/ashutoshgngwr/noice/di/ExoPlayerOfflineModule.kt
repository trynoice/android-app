package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.offline.DefaultDownloadIndex
import com.google.android.exoplayer2.offline.DownloadIndex
import com.google.android.exoplayer2.offline.WritableDownloadIndex
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

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
