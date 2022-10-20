package com.trynoice.api.client.models

/**
 * Request parameters accepted by [update
 * profile][com.trynoice.api.client.apis.AccountApi.updateProfile] operation.
 *
 * @property email email address of the user
 * @property name name of the user
 */
data class UpdateProfileParams(val email: String?, val name: String?)
