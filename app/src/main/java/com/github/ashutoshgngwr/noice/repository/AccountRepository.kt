package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import com.github.ashutoshgngwr.noice.data.AppCacheStore
import com.github.ashutoshgngwr.noice.models.Profile
import com.github.ashutoshgngwr.noice.models.toDomainEntity
import com.github.ashutoshgngwr.noice.models.toRoomDto
import com.github.ashutoshgngwr.noice.repository.errors.AccountTemporarilyLockedError
import com.github.ashutoshgngwr.noice.repository.errors.DuplicateEmailError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.NotSignedInError
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SignInParams
import com.trynoice.api.client.models.SignUpParams
import com.trynoice.api.client.models.UpdateProfileParams
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
  private val cacheStore: AppCacheStore,
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
    loadFromCache = { cacheStore.profile().get()?.toDomainEntity() },
    loadFromNetwork = { apiClient.accounts().getProfile().toDomainEntity() },
    cacheNetworkResult = { cacheStore.profile().save(it.toRoomDto()) },
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
      loadFromNetwork = { apiClient.accounts().updateProfile(UpdateProfileParams(email, name)) },
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
   * @see fetchNetworkBoundResource
   * @see Resource
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
   * @see fetchNetworkBoundResource
   * @see Resource
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
      val timeoutSeconds = response.headers()["Retry-After"]?.toIntOrNull() ?: 0
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
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun signInWithToken(token: String): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      apiClient.signInWithToken(token)
      if (!apiClient.isSignedIn()) {
        throw NotSignedInError
      }
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "signInWithToken:", e)
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
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun signOut(): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      apiClient.signOut()
      // clear account related cache.
      cacheStore.withTransaction {
        cacheStore.profile().remove()
        cacheStore.subscriptions().removeAll()
      }
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "signOut:", e)
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
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun deleteAccount(accountId: Long): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = { apiClient.accounts().delete(accountId) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "deleteAccount:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  companion object {
    private const val LOG_TAG = "AccountRepository"
  }
}
