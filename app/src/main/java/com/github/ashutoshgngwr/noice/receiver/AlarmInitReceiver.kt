package com.github.ashutoshgngwr.noice.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class AlarmInitReceiver : BroadcastReceiver() {

  @set:Inject
  internal lateinit var alarmRepository: AlarmRepository

  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action !in ACCEPTABLE_INTENT_ACTIONS) {
      return
    }

    runBlocking {
      if (alarmRepository.canScheduleAlarms()) {
        Log.d(LOG_TAG, "onReceive: rescheduling alarms")
        alarmRepository.rescheduleAll()
      } else {
        Log.d(LOG_TAG, "onReceive: disabling alarms due to missing permission")
        alarmRepository.disableAll()
      }
    }
  }

  companion object {
    const val ACTION_INIT = "init"

    private const val LOG_TAG = "AlarmInitReceiver"
    private val ACCEPTABLE_INTENT_ACTIONS = setOfNotNull(
      ACTION_INIT,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
      else null,
      Intent.ACTION_BOOT_COMPLETED,
      "android.intent.action.QUICKBOOT_POWERON",
      Intent.ACTION_TIME_CHANGED,
      Intent.ACTION_TIMEZONE_CHANGED,
    )
  }
}
