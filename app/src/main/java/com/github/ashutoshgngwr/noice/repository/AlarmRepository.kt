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
import java.util.concurrent.TimeUnit

class AlarmRepository(
  private val alarmManager: AlarmManager,
  private val presetRepository: PresetRepository,
  private val appDb: AppDatabase,
  private val pendingIntentBuilder: PendingIntentBuilder,
) {

  suspend fun save(alarm: Alarm) {
    appDb.alarms().save(alarm.toRoomDto())
    alarmManager.cancel(alarm)
    if (alarm.isEnabled) {
      alarmManager.setAlarmClock(alarm)
    }
  }

  suspend fun delete(alarm: Alarm) {
    alarmManager.cancel(alarm)
    appDb.alarms().deleteById(alarm.id)
  }

  suspend fun get(alarmId: Int): Alarm? {
    return appDb.alarms()
      .getById(alarmId)
      ?.let { it.toDomainEntity(presetRepository.get(it.presetId)) }
  }

  fun pagingDataFlow(): Flow<PagingData<Alarm>> {
    return Pager(PagingConfig(pageSize = 20)) { appDb.alarms().pagingSource() }
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

  suspend fun reportTrigger(alarmId: Int, isSnoozed: Boolean) {
    val alarm = get(alarmId) ?: return
    if (alarm.weeklySchedule == 0) {
      save(alarm.copy(isEnabled = false))
    }

    alarmManager.cancel(alarm)
    if (isSnoozed) {
      // TODO: add an option in the settings for Snooze duration.
      alarmManager.setAlarmClock(alarm, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10))
    } else if (alarm.weeklySchedule != 0) {
      alarmManager.setAlarmClock(alarm)
    }
  }

  suspend fun rescheduleAll() {
    val presets = presetRepository.list().associateBy { it.id }
    appDb.alarms()
      .listEnabled()
      .map { it.toDomainEntity(presets[it.presetId]) }
      .forEach { alarm ->
        alarmManager.cancel(alarm)
        alarmManager.setAlarmClock(alarm)
      }
  }

  private fun AlarmManager.setAlarmClock(alarm: Alarm) {
    setAlarmClock(alarm, alarm.getTriggerTimeMillis())
  }

  private fun AlarmManager.setAlarmClock(alarm: Alarm, triggerTimeMillis: Long) {
    AlarmClockInfo(triggerTimeMillis, pendingIntentBuilder.buildShowIntent(alarm))
      .also { setAlarmClock(it, pendingIntentBuilder.buildTriggerIntent(alarm)) }
  }

  private fun AlarmManager.cancel(alarm: Alarm) {
    cancel(pendingIntentBuilder.buildTriggerIntent(alarm))
  }

  interface PendingIntentBuilder {
    fun buildShowIntent(alarm: Alarm): PendingIntent
    fun buildTriggerIntent(alarm: Alarm): PendingIntent
  }
}
