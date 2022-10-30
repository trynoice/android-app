package com.github.ashutoshgngwr.noice.repository

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.os.Build
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.models.toDomainEntity
import com.github.ashutoshgngwr.noice.models.toRoomDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(
  private val alarmManager: AlarmManager,
  private val presetRepository: PresetRepository,
  private val appDb: AppDatabase,
  private val pendingIntentBuilder: PendingIntentBuilder,
) {

  suspend fun create(minuteOfDay: Int) {
    val alarm = Alarm(
      id = 0,
      label = null,
      isEnabled = canScheduleAlarms(),
      minuteOfDay = minuteOfDay,
      weeklySchedule = 0,
      preset = null,
      vibrate = true,
    )

    appDb.alarms().save(alarm.toRoomDto())
    if (alarm.isEnabled) {
      schedule(alarm)
    }
  }

  fun list(): Flow<PagingData<Alarm>> {
    return Pager(PagingConfig(pageSize = 20)) { appDb.alarms().list() }
      .flow
      .map { pagingData ->
        pagingData.map { it.toDomainEntity(presetRepository.get(it.presetId)) }
      }
  }

  fun canScheduleAlarms(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      return alarmManager.canScheduleExactAlarms()
    }

    return true
  }

  private fun schedule(alarm: Alarm) {
    AlarmClockInfo(alarm.getTriggerTimeMillis(), pendingIntentBuilder.buildShowIntent(alarm))
      .also { alarmManager.setAlarmClock(it, pendingIntentBuilder.buildTriggerIntent(alarm)) }
  }

  interface PendingIntentBuilder {
    fun buildShowIntent(alarm: Alarm): PendingIntent
    fun buildTriggerIntent(alarm: Alarm): PendingIntent
  }
}
