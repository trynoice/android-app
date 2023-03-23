package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preset")
data class PresetDto(
  @PrimaryKey val id: String,
  val name: String,
  val soundStatesJson: String,
)

@Entity(tableName = "default_presets_sync_version")
data class DefaultPresetsSyncVersionDto(
  val version: Int,
  @PrimaryKey val roomId: Int = 0,
)
