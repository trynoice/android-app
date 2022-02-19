package com.trynoice.api.client

import android.content.Context
import com.google.gson.Gson
import com.trynoice.api.client.apis.AccountApi
import com.trynoice.api.client.apis.InternalAccountApi
import com.trynoice.api.client.apis.SubscriptionApi
import com.trynoice.api.client.auth.AccessTokenInjector
import com.trynoice.api.client.auth.AuthCredentialRepository
import com.trynoice.api.client.auth.RefreshTokenInjector
import com.trynoice.api.client.models.AuthCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create


/**
 * A thin wrapper around Retrofit to bundle together the networked API calls while transparently
 * managing authentication credentials.
 */
class NoiceApiClient(
  context: Context,
  gson: Gson,
  baseUrl: String = "https://api.trynoice.com",
  userAgent: String = "noice-api-client"
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
    .addInterceptor { chain ->
      chain.proceed(
        chain.request()
          .newBuilder()
          .addHeader("User-Agent", "$userAgent OkHttp/${OkHttp.VERSION}")
          .build()
      )
    }
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

  fun getSignedInState(): StateFlow<Boolean> = signedInState

  /**
   * Signs out the currently logged in user.
   *
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  suspend fun signOut() {
    credentialRepository.getRefreshToken() ?: return
    runCatching { internalAccountApi.signOut() }
      .onFailure { throw it }
      .onSuccess { response ->
        // HTTP 401 = invalid or expired refresh token, thus a valid result.
        if (!response.isSuccessful && response.code() != 401) {
          throw HttpException(response)
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
            throw it
          }
        }
    }
  }
}
