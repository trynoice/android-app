package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.SoundTagDto
import java.io.Serializable
import com.trynoice.api.client.models.SoundTag as ApiSoundTag

data class SoundTag(val id: String, val name: String) : Serializable

fun ApiSoundTag.toRoomDto(): SoundTagDto {
  return SoundTagDto(id = id, name = name)
}

fun List<ApiSoundTag>.toRoomDto(): List<SoundTagDto> {
  return map { it.toRoomDto() }
}

fun SoundTagDto.toDomainEntity(): SoundTag {
  return SoundTag(id = id, name = name)
}

fun List<SoundTagDto>.toDomainEntity(): List<SoundTag> {
  return map { it.toDomainEntity() }
}
