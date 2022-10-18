package com.github.ashutoshgngwr.noice.data.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "sound")
data class SoundMinimalDto(
  @PrimaryKey val id: String,
  val groupId: String,
  val name: String,
  val iconSvg: String,
  val maxSilence: Int,
  val isPremium: Boolean,
)

@Entity(tableName = "sound_segment", primaryKeys = ["soundId", "name"])
data class SoundSegmentDto(
  val soundId: String,
  val name: String,
  val basePath: String,
  val isFree: Boolean,
  val isBridgeSegment: Boolean,
  val from: String?,
  val to: String?,
)

@Entity(tableName = "sounds_tags", primaryKeys = ["soundId", "tagId"])
data class SoundTagCrossRef(
  val soundId: String,
  @ColumnInfo(index = true) val tagId: String,
)

@Entity(
  tableName = "sound_source",
  indices = [Index(value = ["soundId", "name", "url"], unique = true)]
)
data class SoundSourceDto(
  val soundId: String,
  val name: String,
  val url: String,
  val license: String,
  val authorName: String?,
  val authorUrl: String?,
  @PrimaryKey(autoGenerate = true) val roomId: Long = 0,
)

data class SoundDto(
  @Embedded val sound: SoundMinimalDto,

  @Relation(parentColumn = "groupId", entityColumn = "id")
  val group: SoundGroupDto,

  @Relation(parentColumn = "id", entityColumn = "soundId")
  val segments: List<SoundSegmentDto>,

  @Relation(
    parentColumn = "id", // sound id
    entity = SoundTagDto::class,
    entityColumn = "id", // sound tag id
    associateBy = Junction(
      value = SoundTagCrossRef::class,
      parentColumn = "soundId",
      entityColumn = "tagId"
    )
  )
  val tags: List<SoundTagDto>,

  @Relation(
    parentColumn = "id",
    entityColumn = "soundId"
  )
  val sources: List<SoundSourceDto>
)
