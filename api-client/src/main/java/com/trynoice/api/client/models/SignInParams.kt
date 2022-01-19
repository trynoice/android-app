package com.trynoice.api.client.models

import com.google.gson.annotations.Expose

/**
 * Request parameters accepted by [account sign-up][com.trynoice.api.client.apis.AccountApi.signIn]
 * operation.
 *
 * @param email email address of the user
 */
data class SignInParams(

  @Expose
  val email: String
)
