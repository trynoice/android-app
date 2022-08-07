package com.github.ashutoshgngwr.noice

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.offline.DefaultDownloadIndex
import com.google.android.exoplayer2.offline.DownloadIndex
import com.google.android.exoplayer2.offline.WritableDownloadIndex
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ashutoshgngwr.may.May
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class NoiceApplication : Application(), Configuration.Provider {

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @set:Inject
  internal lateinit var workerFactory: HiltWorkerFactory

  override fun onCreate() {
    super.onCreate()
    DynamicColorsOptions.Builder()
      .setPrecondition { _, _ -> settingsRepository.shouldUseMaterialYouColors() }
      .build()
      .also { DynamicColors.applyToActivitiesIfAvailable(this, it) }
  }

  override fun getWorkManagerConfiguration(): Configuration {
    return Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .build()
  }

  @Module
  @InstallIn(SingletonComponent::class)
  object GsonModule {
    @Provides
    @Singleton
    fun gson(): Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
  }

  @Module
  @InstallIn(SingletonComponent::class)
  object ApiModule {
    @Provides
    @Singleton
    fun client(@ApplicationContext context: Context, gson: Gson): NoiceApiClient {
      return NoiceApiClient(
        context = context,
        gson = gson,
        userAgent = "${context.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} " +
          "(Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL})",
      )
    }
  }

  @Module
  @InstallIn(SingletonComponent::class)
  object CacheStoreModule {
    @Provides
    @Singleton
    fun cacheStore(@ApplicationContext context: Context): May {
      val cacheStoreDir = File(context.filesDir, "api-client-cache").also { it.mkdirs() }
      val cacheStoreFile = File(cacheStoreDir, "${BuildConfig.VERSION_NAME}.may.db")

      // delete old cache stores. a single store may have multiple files (with same basename).
      cacheStoreDir.listFiles { f -> !f.name.startsWith(BuildConfig.VERSION_NAME) }
        ?.forEach { f -> f.deleteRecursively() }

      return May.openOrCreateDatastore(cacheStoreFile)
    }
  }

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
}
