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

  @Query("DELETE FROM alarm WHERE id = :alarmId")
  abstract suspend fun deleteById(alarmId: Int)

  @Query("SELECT * FROM alarm WHERE id = :alarmId")
  abstract suspend fun getById(alarmId: Int): AlarmDto?

  @Query("SELECT * FROM alarm WHERE isEnabled = 1 ORDER BY minuteOfDay ASC")
  abstract suspend fun listEnabled(): List<AlarmDto>

  @Query("SELECT * FROM alarm ORDER BY minuteOfDay ASC")
  abstract fun pagingSource(): PagingSource<Int, AlarmDto>
}
