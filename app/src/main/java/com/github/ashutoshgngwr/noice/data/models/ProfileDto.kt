package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "profile")
data class ProfileDto(
  val accountId: Long,
  val email: String,
  val name: String,
  @PrimaryKey val roomId: Int = 0,
)
