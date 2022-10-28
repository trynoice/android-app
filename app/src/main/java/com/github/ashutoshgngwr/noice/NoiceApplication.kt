package com.github.ashutoshgngwr.noice

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.Room
import androidx.work.Configuration
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.receiver.AlarmReceiver
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
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
    fun gson(): Gson = GsonBuilder().create()
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
  object AppDatabaseModule {
    @Provides
    @Singleton
    fun appDatabase(@ApplicationContext context: Context): AppDatabase {
      // TODO: remove in future versions
      // clear old cache data store.
      context.deleteDatabase("app-cache.may.db")
      return Room.databaseBuilder(context, AppDatabase::class.java, "${context.packageName}.db")
        .build()
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

  @Module
  @InstallIn(SingletonComponent::class)
  object AlarmRepositoryModule {
    @Provides
    @Singleton
    fun alarmRepository(@ApplicationContext context: Context, appDb: AppDatabase): AlarmRepository {
      val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else PendingIntent.FLAG_UPDATE_CURRENT

      val piBuilder = object : AlarmRepository.PendingIntentBuilder {
        override fun buildShowIntent(alarm: Alarm): PendingIntent {
          // TODO: focus the specified alarm in the list
          return Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_NAV_DESTINATION, R.id.home_alarms)
            .let { PendingIntent.getActivity(context, alarm.id, it, piFlags) }
        }

        override fun buildTriggerIntent(alarm: Alarm): PendingIntent {
          return Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            .let { PendingIntent.getBroadcast(context, alarm.id, it, piFlags) }
        }
      }

      return AlarmRepository(requireNotNull(context.getSystemService()), appDb, piBuilder)
    }
  }
}
