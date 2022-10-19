package com.github.ashutoshgngwr.noice.data.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

data class SoundInfoDto(
  @Embedded val metadata: SoundMetadataDto,

  @Relation(parentColumn = "groupId", entityColumn = "id")
  val group: SoundGroupDto,

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

@Entity(tableName = "sound_metadata")
data class SoundMetadataDto(
  @PrimaryKey val id: String,
  val groupId: String,
  val name: String,
  val iconSvg: String,
  val maxSilence: Int,
  val isPremium: Boolean,
  val hasPremiumSegments: Boolean,
)

@Entity(tableName = "sounds_tags", primaryKeys = ["soundId", "tagId"])
data class SoundTagCrossRef(
  val soundId: String,
  @ColumnInfo(index = true) val tagId: String,
)

@Entity(tableName = "sound_source", primaryKeys = ["soundId", "name", "url"])
data class SoundSourceDto(
  val soundId: String,
  val name: String,
  val url: String,
  val license: String,
  val authorName: String?,
  val authorUrl: String?,
)
