package com.github.ashutoshgngwr.noice.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.ashutoshgngwr.noice.data.models.AlarmDto

@Dao
abstract class AlarmDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun save(alarm: AlarmDto)

  @Query("SELECT * FROM alarm ORDER BY minuteOfDay ASC")
  abstract fun list(): PagingSource<Int, AlarmDto>
}
