package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_tag")
data class SoundTagDto(
  @PrimaryKey val id: String,
  val name: String,
)
