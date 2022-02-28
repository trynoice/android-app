package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable

/**
 * Request parameters accepted by [account sign-up][com.trynoice.api.client.apis.AccountApi.signUp]
 * operation.
 *
 * @param email email address of the user
 * @param name name of the user
 */
data class SignUpParams(

  @Expose
  val email: String,

  @Expose
  val name: String
) : Serializable
