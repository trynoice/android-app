package com.trynoice.api.client.models

/**
 * Profile data of an authenticated user returned by
 * [account get-profile][com.trynoice.api.client.apis.AccountApi.getProfile] operation.
 *
 * @property accountId id of the account
 * @property email email of the user associated with the account
 * @property name name of the user associated with the account
 */
data class Profile(
  val accountId: Long,
  val email: String,
  val name: String
)
