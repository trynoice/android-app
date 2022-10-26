package com.github.ashutoshgngwr.noice.provider

import android.app.AlarmManager
import android.os.Build

class AlarmProvider(private val alarmManager: AlarmManager) {

  fun canScheduleAlarms(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      return alarmManager.canScheduleExactAlarms()
    }

    return true
  }
}
