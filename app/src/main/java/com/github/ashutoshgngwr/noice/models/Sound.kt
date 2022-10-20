package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.SoundDto

data class Sound(val info: SoundInfo, val segments: List<SoundSegment>)

fun SoundDto.toDomainEntity(): Sound {
  return Sound(info = info.toDomainEntity(), segments = segments.toDomainEntity())
}
