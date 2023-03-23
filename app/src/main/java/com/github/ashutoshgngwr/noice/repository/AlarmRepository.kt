package com.github.ashutoshgngwr.noice.repository

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.os.Build
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.models.toDomainEntity
import com.github.ashutoshgngwr.noice.models.toRoomDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(
  private val alarmManager: AlarmManager,
  private val presetRepository: PresetRepository,
  private val settingsRepository: SettingsRepository,
  private val appDb: AppDatabase,
  private val pendingIntentBuilder: PendingIntentBuilder,
) {

  suspend fun save(alarm: Alarm): Int {
    val alarmId = appDb.alarms().save(alarm.toRoomDto())
    val saved = alarm.copy(id = alarmId.toInt())
    alarmManager.cancel(saved)
    if (saved.isEnabled) {
      alarmManager.setAlarmClock(saved)
    }

    return saved.id
  }

  suspend fun delete(alarm: Alarm) {
    alarmManager.cancel(alarm)
    appDb.alarms().deleteById(alarm.id)
  }

  suspend fun get(alarmId: Int): Alarm? {
    val alarmDto = appDb.alarms().getById(alarmId)
    val preset = alarmDto?.presetId?.let { presetRepository.get(it) }
    return alarmDto?.toDomainEntity(preset)
  }

  fun countEnabled(): Flow<Int> {
    return appDb.alarms().countEnabledFlow()
  }

  fun pagingDataFlow(): Flow<PagingData<Alarm>> {
    return Pager(PagingConfig(pageSize = 20)) { appDb.alarms().pagingSource() }
      .flow
      .map { pagingData ->
        pagingData.map { alarmDto ->
          val preset = alarmDto.presetId?.let { presetRepository.get(it) }
          alarmDto.toDomainEntity(preset)
        }
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
      appDb.alarms().save(alarm.copy(isEnabled = false).toRoomDto())
    }

    alarmManager.cancel(alarm)
    if (isSnoozed) {
      val snoozeMillis = settingsRepository.getAlarmSnoozeDuration().inWholeMilliseconds
      alarmManager.setAlarmClock(alarm, System.currentTimeMillis() + snoozeMillis)
    } else if (alarm.weeklySchedule != 0) {
      alarmManager.setAlarmClock(alarm)
    }
  }

  suspend fun rescheduleAll() {
    appDb.alarms()
      .listEnabled()
      .map { it.toDomainEntity(null) } // preset association is not needed here
      .forEach { alarm ->
        alarmManager.cancel(alarm)
        alarmManager.setAlarmClock(alarm)
      }
  }

  suspend fun disableAll(offset: Int = 0): Int {
    var disabledCount = 0
    appDb.withTransaction {
      appDb.alarms()
        .listEnabled()
        .forEachIndexed { index, alarmDto ->
          if (index < offset) return@forEachIndexed
          alarmManager.cancel(alarmDto.toDomainEntity(null)) // preset association is not needed here
          appDb.alarms().save(alarmDto.copy(isEnabled = false))
          disabledCount++
        }
    }

    return disabledCount
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
