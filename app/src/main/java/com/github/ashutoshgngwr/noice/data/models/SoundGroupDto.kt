package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_group")
data class SoundGroupDto(
  @PrimaryKey val id: String,
  val name: String,
)
