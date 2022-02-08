package com.trynoice.api.client.apis

import com.trynoice.api.client.auth.annotations.NeedsRefreshToken
import com.trynoice.api.client.models.AuthCredentials
import retrofit2.http.GET

/**
 * Wraps APIs that are internally used by the API Client. Since API Client needs to perform other
 * operations with these, they are hidden from the users of the client to minimise confusion.
 */
internal interface InternalAccountApi {

  /**
   * Issues fresh credentials (refresh and access tokens) in exchange for a valid refresh token. If
   * the refresh token is invalid, expired or re-used, it returns `HTTP 401`.
   *
   * Responses:
   *  - 200: fresh credentials (refresh and access tokens).
   *  - 400: request is not valid.
   *  - 401: refresh token is invalid, expired or re-used.
   *  - 500: internal server error.
   *
   * @return fresh [AuthCredentials] on successful request.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsRefreshToken
  @GET("/v1/accounts/credentials")
  suspend fun issueCredentials(): AuthCredentials

  /**
   * Revokes a valid refresh token. If the refresh token is invalid, expired or re-used, it returns
   * HTTP 401.
   *
   * Responses:
   *  - 200: OK.
   *  - 400: request is not valid.
   *  - 401: refresh token is invalid, expired or re-used.
   *  - 500: internal server error.
   *
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsRefreshToken
  @GET("/v1/accounts/signOut")
  suspend fun signOut()
}
