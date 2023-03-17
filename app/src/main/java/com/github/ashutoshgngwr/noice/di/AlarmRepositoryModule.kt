package com.github.ashutoshgngwr.noice.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.fragment.AlarmsFragmentArgs
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.service.AlarmRingerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AlarmRepositoryModule {

  @Provides
  @Singleton
  fun alarmRepository(
    @ApplicationContext context: Context,
    appDb: AppDatabase,
    presetRepository: PresetRepository,
    settingsRepository: SettingsRepository,
  ): AlarmRepository {
    val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else PendingIntent.FLAG_UPDATE_CURRENT

    val piBuilder = object : AlarmRepository.PendingIntentBuilder {
      override fun buildShowIntent(alarm: Alarm): PendingIntent {
        val alarmsFragmentArgs = AlarmsFragmentArgs(alarm.id).toBundle()
        return Intent(context, MainActivity::class.java)
          .putExtra(MainActivity.EXTRA_HOME_DESTINATION, R.id.alarms)
          .putExtra(MainActivity.EXTRA_HOME_DESTINATION_ARGS, alarmsFragmentArgs)
          .let { PendingIntent.getActivity(context, alarm.id, it, piFlags) }
      }

      override fun buildTriggerIntent(alarm: Alarm): PendingIntent {
        return AlarmRingerService.buildRingIntent(context, alarm.id)
          .let { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              PendingIntent.getForegroundService(context, alarm.id, intent, piFlags)
            } else {
              PendingIntent.getService(context, alarm.id, intent, piFlags)
            }
          }
      }
    }

    return AlarmRepository(
      requireNotNull(context.getSystemService()),
      presetRepository,
      settingsRepository,
      appDb,
      piBuilder,
    )
  }
}
