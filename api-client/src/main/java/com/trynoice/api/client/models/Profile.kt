package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable

/**
 * Profile data of an authenticated user returned by
 * [account get-profile][com.trynoice.api.client.apis.AccountApi.getProfile] operation.
 *
 * @param accountId id of the account
 * @param email email of the user associated with the account
 * @param name name of the user associated with the account
 */
data class Profile(

  @Expose
  val accountId: Long,

  @Expose
  val email: String,

  @Expose
  val name: String
) : Serializable
