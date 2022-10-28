package com.github.ashutoshgngwr.noice.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context?, intent: Intent?) {
    val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: return
    Log.d("AlarmReceiver", "onReceive: received: alarmId=$alarmId")
  }

  companion object {
    const val EXTRA_ALARM_ID = "alarmId"
  }
}
