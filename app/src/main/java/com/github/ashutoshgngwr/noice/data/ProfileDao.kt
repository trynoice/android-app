package com.github.ashutoshgngwr.noice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.ashutoshgngwr.noice.data.models.ProfileDto

@Dao
abstract class ProfileDao {

  @Query("SELECT * FROM profile LIMIT 1")
  abstract suspend fun get(): ProfileDto?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun save(profile: ProfileDto)

  @Query("DELETE FROM profile")
  abstract suspend fun remove()
}
