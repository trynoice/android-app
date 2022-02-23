package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import com.github.ashutoshgngwr.noice.model.AccountTemporarilyLockedError
import com.github.ashutoshgngwr.noice.model.DuplicateEmailError
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.model.NotSignedInError
import com.github.ashutoshgngwr.noice.model.Resource
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Profile
import com.trynoice.api.client.models.SignInParams
import com.trynoice.api.client.models.SignUpParams
import com.trynoice.api.client.models.UpdateProfileParams
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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
   * @throws HttpException on api errors.
   */
  fun getProfile(): Flow<Resource<Profile>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs(PROFILE_CACHE_KEY) },
    loadFromNetwork = { apiClient.accounts().getProfile() },
    cacheNetworkResult = { p -> cacheStore.put(PROFILE_CACHE_KEY, p) },
    loadFromNetworkErrorTransform = { e ->
      Log.d(LOG_TAG, "getProfile:", e)
      when {
        e is HttpException && e.code() == 401 -> NotSignedInError
        e is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Updates the profile fields of an authenticated user.
   *
   * @param email must be valid email
   * @param name must be valid name
   * @throws DuplicateEmailError if updated email is already linked to another account.
   * @throws NetworkError on network errors.
   * @throws HttpException on api errors.
   */
  suspend fun updateProfile(email: String, name: String) {
    try {
      val response = apiClient.accounts().updateProfile(UpdateProfileParams(email, name))
      when {
        response.isSuccessful -> return
        response.code() == 409 -> throw DuplicateEmailError
        else -> {
          val e = HttpException(response)
          Log.i(LOG_TAG, "updateProfile: api error", e)
          throw e
        }
      }
    } catch (e: IOException) {
      Log.i(LOG_TAG, "updateProfile: network error", e)
      throw NetworkError
    }
  }

  /**
   * Attempts to send the sign-link to the given [email] address.
   *
   * @throws AccountTemporarilyLockedError if the account is temporarily locked from making sign-in
   * attempts.
   * @throws NetworkError on network errors.
   * @throws HttpException on api errors.
   */
  suspend fun signIn(email: String) {
    try {
      val response = apiClient.accounts().signIn(SignInParams(email))
      handleSignInResponse(response)
    } catch (e: IOException) {
      Log.i(LOG_TAG, "signIn: network error", e)
      throw NetworkError
    } catch (e: HttpException) {
      Log.i(LOG_TAG, "signIn: api error", e)
      throw e
    }
  }

  /**
   * Attempts to create a new account with given [email] and [name] and send a sign-in link to it.
   *
   * @throws AccountTemporarilyLockedError if the account is temporarily locked from making sign-in
   * attempts.
   * @throws NetworkError on network errors.
   * @throws HttpException on api errors.
   */
  suspend fun signUp(email: String, name: String) {
    try {
      val response = apiClient.accounts().signUp(SignUpParams(email, name))
      handleSignInResponse(response)
    } catch (e: IOException) {
      Log.i(LOG_TAG, "signUp: network error", e)
      throw NetworkError
    } catch (e: HttpException) {
      Log.i(LOG_TAG, "signUp: api error", e)
      throw e
    }
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
   * @throws HttpException on api errors.
   */
  suspend fun signInWithToken(token: String) {
    try {
      apiClient.signInWithToken(token)
    } catch (e: IOException) {
      Log.i(LOG_TAG, "signInWithToken: network error", e)
      throw NetworkError
    } catch (e: HttpException) {
      Log.i(LOG_TAG, "signInWithToken: api error", e)
      throw e
    }

    if (!apiClient.isSignedIn()) {
      throw NotSignedInError
    }
  }

  /**
   * Signs out the currently authenticated user from the [NoiceApiClient].
   *
   * @throws NetworkError on network errors.
   * @throws HttpException on api errors.
   */
  suspend fun signOut() {
    try {
      apiClient.signOut()
      cacheStore.removeAll()
    } catch (e: IOException) {
      Log.i(LOG_TAG, "signOut: network error", e)
      throw NetworkError
    } catch (e: HttpException) {
      Log.i(LOG_TAG, "signOut: api error", e)
      throw e
    }
  }

  /**
   * Deletes the account of currently authenticated user.
   *
   * @throws NetworkError on network errors.
   * @throws HttpException on api errors.
   */
  suspend fun deleteAccount(accountId: Long) {
    try {
      val response = apiClient.accounts().delete(accountId)
      if (!response.isSuccessful) {
        val error = HttpException(response)
        Log.i(LOG_TAG, "deleteAccount: api error", error)
        throw error
      }
    } catch (e: IOException) {
      Log.i(LOG_TAG, "deleteAccount: network error", e)
      throw NetworkError
    }
  }

  companion object {
    private const val LOG_TAG = "AccountRepository"
    private const val PROFILE_CACHE_KEY = "account/profile"
  }
}
