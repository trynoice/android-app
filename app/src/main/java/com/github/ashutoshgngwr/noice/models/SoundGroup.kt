package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.SoundGroupDto
import java.io.Serializable
import com.trynoice.api.client.models.SoundGroup as ApiSoundGroup

data class SoundGroup(val id: String, val name: String) : Serializable

fun ApiSoundGroup.toRoomDto(): SoundGroupDto {
  return SoundGroupDto(id = id, name = name)
}

fun SoundGroupDto.toDomainEntity(): SoundGroup {
  return SoundGroup(id = id, name = name)
}

fun List<SoundGroupDto>.toDomainEntity(): List<SoundGroup> {
  return map { it.toDomainEntity() }
}
