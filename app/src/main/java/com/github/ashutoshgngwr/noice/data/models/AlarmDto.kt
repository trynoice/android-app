package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm")
data class AlarmDto(
  @PrimaryKey val id: Long,
  val label: String,
  val isEnabled: Boolean,
  val minuteOfDay: Int,
  val weeklySchedule: Short,
  val presetId: String,
  val vibrate: Boolean,
)
