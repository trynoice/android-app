package com.trynoice.api.client

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.trynoice.api.client.apis.AccountApi
import com.trynoice.api.client.apis.InternalAccountApi
import com.trynoice.api.client.apis.SubscriptionApi
import com.trynoice.api.client.auth.AccessTokenInjector
import com.trynoice.api.client.auth.AuthCredentialRepository
import com.trynoice.api.client.auth.RefreshTokenInjector
import com.trynoice.api.client.models.AuthCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.io.IOException

private val LOG_TAG = NoiceApiClient::class.simpleName

/**
 * A thin wrapper around Retrofit to bundle together the networked API calls while transparently
 * managing authentication credentials.
 */
class NoiceApiClient(
  context: Context,
  gson: Gson,
  baseUrl: String = "https://api.trynoice.com",
) {

  private val credentialRepository = AuthCredentialRepository(context)
  private val signedInState = MutableStateFlow(credentialRepository.getRefreshToken() != null)
  private val refreshCredentialsMutex = Mutex()

  private val okhttpClient = OkHttpClient.Builder()
    .addInterceptor(AccessTokenInjector(credentialRepository, credentialRefresher = {
      runBlocking { refreshCredentials() }
    }))
    .addInterceptor(RefreshTokenInjector(credentialRepository))
    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
    .build()

  private val retrofit: Retrofit by lazy {
    Retrofit.Builder()
      .client(okhttpClient)
      .baseUrl(baseUrl)
      .addConverterFactory(
        GsonConverterFactory.create(gson)
      )
      .build()
  }

  private val accountApi: AccountApi by lazy { retrofit.create() }
  private val internalAccountApi: InternalAccountApi by lazy { retrofit.create() }
  private val subscriptionApi: SubscriptionApi by lazy { retrofit.create() }

  /**
   * Subscription management related APIs.
   */
  fun accounts() = accountApi

  /**
   * Account and user management related APIs.
   */
  fun subscriptions() = subscriptionApi

  /**
   * Adds the sign-in token to the credential store and then attempts to issue new credentials using
   * it.
   */
  suspend fun signInWithToken(signInToken: String) {
    credentialRepository.setCredentials(AuthCredentials(signInToken, ""))
    refreshCredentials()
    signedInState.emit(credentialRepository.getRefreshToken() != null)
  }

  fun isSignedIn(): Boolean = signedInState.value

  fun getSignedInState(): Flow<Boolean> = signedInState

  /**
   * Signs out the currently logged in user.
   */
  @Throws(IOException::class, HttpException::class)
  suspend fun signOut() {
    credentialRepository.getRefreshToken() ?: return
    runCatching { internalAccountApi.signOut() }
      .onFailure {
        // HTTP 401 = invalid or expired refresh token, thus a valid result.
        if (it !is HttpException || it.code() != 401) {
          throw it
        }
      }

    credentialRepository.clearCredentials()
    signedInState.emit(false)
  }

  private suspend fun refreshCredentials() {
    val oldRefreshToken = credentialRepository.getRefreshToken() ?: return
    refreshCredentialsMutex.withLock {
      // credentials were refreshed while we were waiting for the lock.
      if (oldRefreshToken != credentialRepository.getRefreshToken()) {
        return
      }

      runCatching { internalAccountApi.issueCredentials() }
        .onSuccess { credentialRepository.setCredentials(it) }
        .onFailure {
          if (it is HttpException && it.code() == 401) {
            credentialRepository.clearCredentials()
            signedInState.emit(false)
          } else {
            Log.w(LOG_TAG, "refresh credential request failed", it)
          }
        }
    }
  }
}
