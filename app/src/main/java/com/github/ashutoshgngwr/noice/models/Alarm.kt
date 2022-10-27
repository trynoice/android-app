package com.github.ashutoshgngwr.noice.models

import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.data.models.AlarmDto
import com.github.ashutoshgngwr.noice.model.Preset
import java.util.*

data class Alarm(
  val id: Int,
  val label: String?,
  val isEnabled: Boolean,
  val minuteOfDay: Int,
  val weeklySchedule: Int,
  val preset: Preset?,
  val vibrate: Boolean,
) {

  fun getTriggerTimeMillis(): Long {
    return getTriggerTimeMillis(System.currentTimeMillis())
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun getTriggerTimeMillis(currentTimeInMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = currentTimeInMillis

    val currentMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
    val daysToAdd = if (weeklySchedule == 0) {
      if (minuteOfDay < currentMinuteOfDay) 1 else 0
    } else if ((weeklySchedule shr currentDay - 1) and 1 == 1 && minuteOfDay > currentMinuteOfDay) {
      0
    } else {
      var toAdd = 1
      while (true) {
        if ((weeklySchedule shr ((currentDay + toAdd - 1) % 7)) and 1 == 1) break
        toAdd++
      }
      toAdd
    }

    calendar.add(Calendar.DAY_OF_MONTH, daysToAdd)
    calendar.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
    calendar.set(Calendar.MINUTE, minuteOfDay % 60)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }
}

fun AlarmDto.toDomainEntity(preset: Preset?): Alarm {
  return Alarm(
    id = id,
    label = label,
    isEnabled = isEnabled,
    minuteOfDay = minuteOfDay,
    weeklySchedule = weeklySchedule,
    preset = preset,
    vibrate = vibrate,
  )
}

fun Alarm.toRoomDto(): AlarmDto {
  return AlarmDto(
    id = id,
    label = label,
    isEnabled = isEnabled,
    minuteOfDay = minuteOfDay,
    weeklySchedule = weeklySchedule,
    presetId = preset?.id,
    vibrate = vibrate,
  )
}
