package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import com.github.ashutoshgngwr.noice.model.AccountTemporarilyLockedError
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.model.NotSignedInError
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Profile
import com.trynoice.api.client.models.SignInParams
import com.trynoice.api.client.models.SignUpParams
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements a data access layer for fetching and manipulating user account related data.
 */
@Singleton
class AccountRepository @Inject constructor(
  private val apiClient: NoiceApiClient,
  private val cacheStore: May,
) {

  /**
   * @return a [StateFlow] that notifies changes to the current signed-in state of the api client.
   */
  fun isSignedIn(): StateFlow<Boolean> = apiClient.getSignedInState()

  /**
   * @return the [Profile] of the user.
   * @throws NotSignedInError if the client isn't signed-in.
   * @throws NetworkError on network errors.
   * @throws Throwable on unknown errors.
   */
  fun getProfile(): Flow<Profile> = flow {
    if (!apiClient.isSignedIn()) {
      throw NotSignedInError
    }

    // immediately emit cached profile if exists, and then refresh it from the network.
    cacheStore.getAs<Profile>(PROFILE_CACHE_KEY)?.also { emit(it) }
    try {
      val profile = apiClient.accounts().getProfile()
      cacheStore.put(PROFILE_CACHE_KEY, profile)
      emit(profile)
    } catch (e: IOException) {
      Log.i(LOG_TAG, "network error when loading profile", e)
      throw NetworkError
    } catch (e: HttpException) {
      Log.i(LOG_TAG, "api error when loading profile", e)
      if (e.code() == 401) {
        throw NotSignedInError
      }

      throw e
    }
  }

  /**
   * Attempts to send the sign-link to the given [email] address.
   *
   * @throws AccountTemporarilyLockedError if the account is temporarily locked from making sign-in
   * attempts.
   * @throws NetworkError on network errors.
   * @throws Throwable on unknown errors.
   */
  suspend fun signIn(email: String) {
    val response: Response<Unit>
    try {
      response = apiClient.accounts().signIn(SignInParams(email))
    } catch (e: IOException) {
      Log.i(LOG_TAG, "network error when loading profile", e)
      throw NetworkError
    } catch (e: HttpException) {
      Log.i(LOG_TAG, "api error when loading profile", e)
      throw e
    }

    handleSignInResponse(response)
  }

  /**
   * Attempts to create a new account with given [email] and [name] and send a sign-in link to it.
   *
   * @throws AccountTemporarilyLockedError if the account is temporarily locked from making sign-in
   * attempts.
   * @throws NetworkError on network errors.
   * @throws Throwable on unknown errors.
   */
  suspend fun signUp(email: String, name: String) {
    val response: Response<Unit>
    try {
      response = apiClient.accounts().signUp(SignUpParams(email, name))
    } catch (e: IOException) {
      Log.i(LOG_TAG, "network error when loading profile", e)
      throw NetworkError
    } catch (e: HttpException) {
      Log.i(LOG_TAG, "api error when loading profile", e)
      throw e
    }

    handleSignInResponse(response)
  }

  private fun handleSignInResponse(response: Response<Unit>) {
    if (response.isSuccessful) {
      return
    }

    if (response.code() == 429) {
      val timeoutSeconds = response.headers().get("Retry-After")?.toIntOrNull() ?: 0
      throw AccountTemporarilyLockedError(timeoutSeconds)
    }

    throw HttpException(response)
  }

  /**
   * Uses the given sign-in [token] to sign-in the api client.
   *
   * @throws NotSignedInError if the [token] is refused by the api.
   * @throws NetworkError on network errors.
   * @throws Throwable on unknown errors.
   */
  suspend fun signInWithToken(token: String) {
    try {
      apiClient.signInWithToken(token)
    } catch (e: IOException) {
      Log.i(LOG_TAG, "network error when loading profile", e)
      throw NetworkError
    } catch (e: HttpException) {
      Log.i(LOG_TAG, "api error when loading profile", e)
      throw e
    }

    if (!apiClient.isSignedIn()) {
      throw NotSignedInError
    }
  }

  companion object {
    private val LOG_TAG = AccountRepository::class.simpleName

    private const val PROFILE_CACHE_KEY = "account/profile"
  }
}
