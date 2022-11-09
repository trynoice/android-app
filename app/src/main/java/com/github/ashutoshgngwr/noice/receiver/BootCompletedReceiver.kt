package com.github.ashutoshgngwr.noice.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

  @set:Inject
  internal lateinit var alarmRepository: AlarmRepository

  override fun onReceive(context: Context?, intent: Intent?) {
    if (Intent.ACTION_BOOT_COMPLETED != intent?.action && "android.intent.action.QUICKBOOT_POWERON" != intent?.action) {
      return
    }

    runBlocking {
      if (alarmRepository.canScheduleAlarms()) {
        withContext(Dispatchers.IO) { alarmRepository.rescheduleAll() }
      } else {
        withContext(Dispatchers.IO) { alarmRepository.disableAll() }
      }
    }
  }
}
