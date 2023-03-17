package com.github.ashutoshgngwr.noice.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.ashutoshgngwr.noice.data.models.DefaultPresetsSyncVersionDto
import com.github.ashutoshgngwr.noice.data.models.PresetDto
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PresetDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun save(preset: PresetDto)

  @Query("DELETE FROM preset WHERE id = :presetId")
  abstract suspend fun deleteById(presetId: String)

  @Query("DELETE FROM preset")
  abstract suspend fun deleteAll()

  @Query("SELECT * FROM preset WHERE id = :presetId")
  abstract suspend fun getById(presetId: String): PresetDto?

  @Query("SELECT * FROM preset ORDER BY RANDOM() LIMIT 1")
  abstract suspend fun getRandom(): PresetDto?

  @Query("SELECT * FROM preset WHERE soundStatesJson = :soundStatesJson LIMIT 1")
  abstract fun getBySoundStatesJsonFlow(soundStatesJson: String): Flow<PresetDto?>

  @Query("SELECT * FROM preset WHERE name > :currentPresetName ORDER BY name ASC LIMIT 1")
  abstract suspend fun getNextOrderedByName(currentPresetName: String): PresetDto?

  @Query("SELECT * FROM preset WHERE name < :currentPresetName ORDER BY name DESC LIMIT 1")
  abstract suspend fun getPreviousOrderedByName(currentPresetName: String): PresetDto?

  @Query("SELECT * FROM preset ORDER BY name ASC LIMIT 1")
  abstract suspend fun getFirstOrderedByName(): PresetDto?

  @Query("SELECT * FROM preset ORDER BY name DESC LIMIT 1")
  abstract suspend fun getLastOrderedByName(): PresetDto?

  @Query("SELECT * FROM preset ORDER BY name ASC")
  abstract fun pagingSource(): PagingSource<Int, PresetDto>

  @Query("SELECT * FROM preset ORDER BY name ASC")
  abstract suspend fun list(): List<PresetDto>

  @Query("SELECT * FROM preset ORDER BY name ASC")
  abstract fun listFlow(): Flow<List<PresetDto>>

  @Query("SELECT COUNT(*) FROM preset WHERE name = :name")
  abstract suspend fun countByName(name: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveDefaultPresetsSyncVersion(dto: DefaultPresetsSyncVersionDto)

  @Query("SELECT version FROM default_presets_sync_version LIMIT 1")
  abstract suspend fun getDefaultPresetsSyncedVersion(): Int?
}
