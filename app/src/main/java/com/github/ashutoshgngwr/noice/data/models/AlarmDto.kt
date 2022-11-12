package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm")
data class AlarmDto(
  @PrimaryKey(autoGenerate = true) val id: Int,
  val label: String?,
  val isEnabled: Boolean,
  val minuteOfDay: Int,
  val weeklySchedule: Int,
  val presetId: String?,
  val vibrate: Boolean,
)
