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
   * Returns a [Flow] that emits the profile [Resource] of the authenticated user.
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NotSignedInError] if the user is not signed-in.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun getProfile(): Flow<Resource<Profile>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs(PROFILE_CACHE_KEY) },
    loadFromNetwork = { apiClient.accounts().getProfile() },
    cacheNetworkResult = { p -> cacheStore.put(PROFILE_CACHE_KEY, p) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "getProfile:", e)
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
   * On failures, the returned [Flow] emits [Resource.Failure] with:
   * - [DuplicateEmailError] if updated email is already linked to another account.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @param email must be valid email
   * @param name must be valid name
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun updateProfile(email: String, name: String): Flow<Resource<Unit>> =
    fetchNetworkBoundResource(
      loadFromNetwork = {
        val response = apiClient.accounts().updateProfile(UpdateProfileParams(email, name))
        if (!response.isSuccessful) {
          throw HttpException(response)
        }
      },
      loadFromNetworkErrorTransform = { e ->
        Log.i(LOG_TAG, "updateProfile:", e)
        when {
          e is HttpException && e.code() == 409 -> DuplicateEmailError
          e is IOException -> NetworkError
          else -> e
        }
      },
    )

  /**
   * Attempts to send the sign-link to the given [email] address.
   *
   * On failures, the returned [Flow] emits [Resource.Failure] with:
   * - [AccountTemporarilyLockedError] if the account is temporarily locked from making sign-in
   *   attempts.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @param email email that the account is registered with.
   */
  fun signIn(email: String): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      val response = apiClient.accounts().signIn(SignInParams(email))
      handleSignInResponse(response)
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "signIn:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Attempts to create a new account with given [email] and [name] and send a sign-in link to it.
   *
   * On failures, the returned [Flow] emits [Resource.Failure] with:
   * - [AccountTemporarilyLockedError] if the account is temporarily locked from making sign-in
   *   attempts.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @param email address for the new account.
   * @param name name of the user for the new account.
   */
  fun signUp(email: String, name: String): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      val response = apiClient.accounts().signUp(SignUpParams(email, name))
      handleSignInResponse(response)
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "signUp:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

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
   * On failures, the returned [Flow] emits [Resource.Failure] with:
   * - [NotSignedInError] if the [token] is refused by the api.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @param token token obtained from a sign-in link.
   */
  fun signInWithToken(token: String): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      apiClient.signInWithToken(token)
      if (!apiClient.isSignedIn()) {
        throw NotSignedInError
      }
    },
    loadFromNetworkErrorTransform = { e ->
      Log.d(LOG_TAG, "signInWithToken:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Signs out the currently authenticated user from the [NoiceApiClient].
   *
   * On failures, the returned [Flow] emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   */
  fun signOut(): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      apiClient.signOut()
      cacheStore.removeAll()
    },
    loadFromNetworkErrorTransform = { e ->
      Log.d(LOG_TAG, "signOut:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Deletes the account of currently authenticated user.
   *
   * On failures, the returned [Flow] emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   */
  fun deleteAccount(accountId: Long): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      val response = apiClient.accounts().delete(accountId)
      if (!response.isSuccessful) {
        throw HttpException(response)
      }
    },
    loadFromNetworkErrorTransform = { e ->
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  companion object {
    private const val LOG_TAG = "AccountRepository"
    private const val PROFILE_CACHE_KEY = "account/profile"
  }
}
