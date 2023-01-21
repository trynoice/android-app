package com.github.ashutoshgngwr.noice.di

import android.content.Context
import android.content.Intent
import com.github.ashutoshgngwr.noice.activity.AlarmRingerActivity
import com.github.ashutoshgngwr.noice.service.AlarmRingerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AlarmRingerModule {

  @Provides
  @Singleton
  fun uiController(@ApplicationContext context: Context): AlarmRingerService.UiController {
    return object : AlarmRingerService.UiController {
      override fun buildShowIntent(
        alarmId: Int,
        alarmLabel: String?,
        alarmTriggerTime: String,
      ): Intent {
        return AlarmRingerActivity.buildIntent(context, alarmId, alarmLabel, alarmTriggerTime)
      }

      override fun dismiss() {
        AlarmRingerActivity.dismiss(context)
      }
    }
  }

  @Provides
  @Singleton
  fun serviceController(@ApplicationContext context: Context): AlarmRingerActivity.ServiceController {
    return object : AlarmRingerActivity.ServiceController {
      override fun dismiss(alarmId: Int) {
        AlarmRingerService.buildDismissIntent(context, alarmId)
          .also { context.startService(it) }
      }

      override fun snooze(alarmId: Int) {
        AlarmRingerService.buildSnoozeIntent(context, alarmId)
          .also { context.startService(it) }
      }
    }
  }
}
