package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Embedded
import androidx.room.Relation

data class SoundDto(
  @Embedded val info: SoundInfoDto,

  @Relation(parentColumn = "id", entityColumn = "soundId")
  val segments: List<SoundSegmentDto>,
)
