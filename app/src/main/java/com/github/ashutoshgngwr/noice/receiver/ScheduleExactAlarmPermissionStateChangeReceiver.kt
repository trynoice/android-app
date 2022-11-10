package com.github.ashutoshgngwr.noice.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleExactAlarmPermissionStateChangeReceiver : BroadcastReceiver() {

  @set:Inject
  internal lateinit var alarmRepository: AlarmRepository

  override fun onReceive(context: Context?, intent: Intent?) {
    if (intent?.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
      return
    }

    if (!alarmRepository.canScheduleAlarms()) {
      runBlocking { alarmRepository.disableAll() }
    }
  }
}
