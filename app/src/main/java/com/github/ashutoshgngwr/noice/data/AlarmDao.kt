package com.github.ashutoshgngwr.noice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.github.ashutoshgngwr.noice.data.models.AlarmDto

@Dao
abstract class AlarmDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun save(alarm: AlarmDto)
}
