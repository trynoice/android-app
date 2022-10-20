package com.trynoice.api.client.models

/**
 * Request parameters accepted by [account sign-up][com.trynoice.api.client.apis.AccountApi.signUp]
 * operation.
 *
 * @property email email address of the user
 * @property name name of the user
 */
data class SignUpParams(val email: String, val name: String)
