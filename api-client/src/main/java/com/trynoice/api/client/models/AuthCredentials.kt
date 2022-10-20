package com.trynoice.api.client.models

/**
 * Response returned by account issue credentials operation.
 *
 * @property accessToken new access token
 * @property refreshToken rotated refresh token
 */
internal data class AuthCredentials(val refreshToken: String, val accessToken: String)
