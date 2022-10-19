package com.github.ashutoshgngwr.noice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.ashutoshgngwr.noice.data.models.LibraryUpdateTimeDto
import com.github.ashutoshgngwr.noice.data.models.SoundDto
import com.github.ashutoshgngwr.noice.data.models.SoundGroupDto
import com.github.ashutoshgngwr.noice.data.models.SoundInfoDto
import com.github.ashutoshgngwr.noice.data.models.SoundMetadataDto
import com.github.ashutoshgngwr.noice.data.models.SoundSegmentDto
import com.github.ashutoshgngwr.noice.data.models.SoundSourceDto
import com.github.ashutoshgngwr.noice.data.models.SoundTagCrossRef
import com.github.ashutoshgngwr.noice.data.models.SoundTagDto
import java.util.*

@Dao
abstract class SoundDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveGroups(groups: List<SoundGroupDto>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveTags(tags: List<SoundTagDto>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveLibraryUpdateTime(time: LibraryUpdateTimeDto)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveMetadata(info: SoundMetadataDto)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveSegment(segment: SoundSegmentDto)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveSoundTagCrossRefs(refs: List<SoundTagCrossRef>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveSources(sources: List<SoundSourceDto>)

  @Query("SELECT * FROM sound_tag")
  abstract suspend fun listTags(): List<SoundTagDto>

  @Query("SELECT updatedAt FROM library_update_time LIMIT 1")
  abstract suspend fun getLibraryUpdateTime(): Date?

  @Transaction
  @Query("SELECT * FROM sound_metadata")
  abstract suspend fun listInfo(): List<SoundInfoDto>

  @Transaction
  @Query("SELECT * FROM sound_metadata WHERE id = :soundId")
  abstract suspend fun get(soundId: String): SoundDto?

  @Query("SELECT * FROM sound_segment WHERE soundId = :soundId")
  abstract suspend fun listSegments(soundId: String): List<SoundSegmentDto>

  @Query("SELECT COUNT(*) FROM sound_metadata WHERE isPremium = 1")
  abstract suspend fun countPremium(): Int
}
