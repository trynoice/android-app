package com.github.ashutoshgngwr.noice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.ashutoshgngwr.noice.data.models.ProfileDto

@Dao
interface ProfileDao {

  @Query("SELECT * FROM profile LIMIT 1")
  suspend fun get(): ProfileDto?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun update(profile: ProfileDto)

  @Query("DELETE FROM profile")
  suspend fun remove()
}
