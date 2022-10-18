package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "library_update_time")
data class LibraryUpdateTimeDto(
  val updatedAt: Date,
  @PrimaryKey val roomId: Int = 0,
)
