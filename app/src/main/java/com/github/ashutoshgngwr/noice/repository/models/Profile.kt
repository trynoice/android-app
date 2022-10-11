package com.github.ashutoshgngwr.noice.repository.models

import com.github.ashutoshgngwr.noice.data.models.ProfileDto
import com.trynoice.api.client.models.Profile  as ApiProfile

data class Profile(
  val accountId: Long,
  val email: String,
  val name: String,
) {

  fun toRoomDto(): ProfileDto {
    return ProfileDto(accountId, email, name)
  }
}

fun ProfileDto.toDomainEntity(): Profile {
  return Profile(accountId, email, name)
}

fun ApiProfile.toDomainEntity(): Profile {
  return Profile(accountId, email, name)
}
