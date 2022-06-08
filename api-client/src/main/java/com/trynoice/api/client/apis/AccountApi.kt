package com.trynoice.api.client.apis

import com.trynoice.api.client.auth.annotations.NeedsAccessToken
import com.trynoice.api.client.models.Profile
import com.trynoice.api.client.models.SignInParams
import com.trynoice.api.client.models.SignUpParams
import com.trynoice.api.client.models.UpdateProfileParams
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * APIs related to account and user management.
 */
interface AccountApi {

  /**
   * Creates a new user account if one didn't already exist for the provided email. If the account
   * creation is successful or the account already existed, it sends a sign-in link the provided
   * email.
   *
   * If the request returns HTTP 429, then the clients must consider the `Retry-After` HTTP header
   * as the API server will refuse all further sign-up attempts until the given duration has
   * elapsed.
   *
   * Responses:
   * - 201: sign-in link sent to the provided email.
   * - 400: request is not valid.
   * - 429: the account is temporarily blocked from attempting sign-up. Refer `Retry-After` header.
   * - 500: internal server error.
   *
   * @throws java.io.IOException on network error
   */
  @POST("/v1/accounts/signUp")
  suspend fun signUp(@Body signUpParams: SignUpParams): Response<Unit>

  /**
   * Sends the sign-in link the provided email.
   *
   * If the request returns HTTP 429, then the clients must consider the `Retry-After` HTTP header
   * as the API server will refuse all further sign-up attempts until the given duration has
   * elapsed.
   *
   * Responses:
   * - 201: sent sign-in link to the given email if such an account exists.
   * - 400: request is not valid.
   * - 429: the account is temporarily blocked from attempting sign-in. Refer `Retry-After` header.
   * - 500: internal server error.
   *
   * @throws java.io.IOException on network error.
   */
  @POST("/v1/accounts/signIn")
  suspend fun signIn(@Body signInParams: SignInParams): Response<Unit>

  /**
   * Get profile of the auth user.
   *
   * Responses:
   *  - 200: profile of the authenticated user.
   *  - 400: failed to read request.
   *  - 401: access token is invalid.
   *  - 500: internal server error.
   *
   * @return the [Profile] of the authenticated user.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsAccessToken
  @GET("/v1/accounts/profile")
  suspend fun getProfile(): Profile

  /**
   * Updates the profile fields of the authenticated user. It accepts partial updates, i.e., all
   * `null` fields in the request body are ignored.
   *
   * Responses
   *  - 204: profile update successfully
   *  - 400: request is not valid
   *  - 401: access token is invalid
   *  - 409: updated email belongs to another existing account
   *  - 500: internal server error
   *
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsAccessToken
  @PATCH("/v1/accounts/profile")
  suspend fun updateProfile(@Body updateProfileParams: UpdateProfileParams)

  /**
   * Deletes the account of an authenticated user. If the account with the given [accountId] does
   * not belong to the authenticated user, it returns `HTTP 400`.
   *
   * Responses:
   *  - 204: account deleted successfully.
   *  - 400: request is not valid.
   *  - 401: access token is invalid.
   *  - 500: internal server error.
   *
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsAccessToken
  @DELETE("/v1/accounts/{id}")
  suspend fun delete(@Path("id") accountId: Long)
}
