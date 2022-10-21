package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.AlarmDto

data class Alarm(
  val id: Long,
  val label: String,
  val isEnabled: Boolean,
  val minuteOfDay: Int,
  val weeklySchedule: Short,
  val presetId: String,
  val vibrate: Boolean,
)

fun AlarmDto.toDomainEntity(): Alarm {
  return Alarm(
    id = id,
    label = label,
    isEnabled = isEnabled,
    minuteOfDay = minuteOfDay,
    weeklySchedule = weeklySchedule,
    presetId = presetId,
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
    presetId = presetId,
    vibrate = vibrate,
  )
}
