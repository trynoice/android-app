package com.trynoice.api.client.models

import com.google.gson.annotations.Expose

/**
 * Request parameters accepted by [update
 * profile][com.trynoice.api.client.apis.AccountApi.updateProfile] operation.
 *
 * @param email email address of the user
 * @param name name of the user
 */
data class UpdateProfileParams(

  @Expose
  val email: String?,

  @Expose
  val name: String?,
)
