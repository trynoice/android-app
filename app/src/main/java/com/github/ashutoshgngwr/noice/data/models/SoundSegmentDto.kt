package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity

@Entity(tableName = "sound_segment", primaryKeys = ["soundId", "name"])
data class SoundSegmentDto(
  val soundId: String,
  val name: String,
  val basePath: String,
  val isFree: Boolean,
  val isBridgeSegment: Boolean,
  val from: String?,
  val to: String?,
)
