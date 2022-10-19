package com.trynoice.api.client.models

/**
 * Request parameters accepted by [account sign-up][com.trynoice.api.client.apis.AccountApi.signIn]
 * operation.
 *
 * @property email email address of the user
 */
data class SignInParams(val email: String)
