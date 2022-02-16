package com.trynoice.api.client.models

import com.google.gson.annotations.Expose

/**
 * Response returned by account issue credentials operation.
 *
 * @param accessToken new access token
 * @param refreshToken rotated refresh token
 */
internal data class AuthCredentials(

  @Expose
  val refreshToken: String,

  @Expose
  val accessToken: String,
)
