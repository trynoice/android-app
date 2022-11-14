package com.github.ashutoshgngwr.noice

import android.app.Application
import android.content.Intent
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.github.ashutoshgngwr.noice.receiver.AlarmInitReceiver
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

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

    Intent(this, AlarmInitReceiver::class.java)
      .setAction(AlarmInitReceiver.ACTION_INIT)
      .also { sendBroadcast(it) }
  }

  override fun getWorkManagerConfiguration(): Configuration {
    return Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .build()
  }
}
