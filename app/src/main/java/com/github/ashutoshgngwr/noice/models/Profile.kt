package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.ProfileDto
import com.trynoice.api.client.models.Profile  as ApiProfile

data class Profile(
  val accountId: Long,
  val email: String,
  val name: String,
)

fun ProfileDto.toDomainEntity(): Profile {
  return Profile(accountId, email, name)
}

fun ApiProfile.toDomainEntity(): Profile {
  return Profile(accountId, email, name)
}

fun Profile.toRoomDto(): ProfileDto {
  return ProfileDto(accountId, email, name)
}
