package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.github.ashutoshgngwr.noice.model.AccountTemporarilyLockedError
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.model.NotSignedInError
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Profile
import com.trynoice.api.client.models.SignInParams
import com.trynoice.api.client.models.SignUpParams
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(private val apiClient: NoiceApiClient) {

  fun isSignedIn(): LiveData<Boolean> = apiClient.getSignedInState().asLiveData()

  suspend fun getProfile(): Profile {
    if (!apiClient.isSignedIn()) {
      throw NotSignedInError
    }

    try {
      return apiClient.accounts().getProfile()
    } catch (e: IOException) {
      Log.i(LOG_TAG, "network error when loading profile", e)
      throw NetworkError
    } catch (e: Throwable) {
      Log.i(LOG_TAG, "unknown error when loading profile", e)
      throw e
    }
  }

  suspend fun signIn(email: String) {
    val response = apiClient.accounts().signIn(SignInParams(email))
    handleSignInResponse(response)
  }

  suspend fun signUp(email: String, name: String) {
    val response = apiClient.accounts().signUp(SignUpParams(email, name))
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

  suspend fun signInWithToken(token: String) {
    apiClient.signInWithToken(token)
    if (!apiClient.isSignedIn()) {
      throw NotSignedInError
    }
  }

  companion object {
    private val LOG_TAG = AccountRepository::class.simpleName
  }
}
