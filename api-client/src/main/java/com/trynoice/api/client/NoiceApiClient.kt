package com.trynoice.api.client

import android.content.Context
import com.google.gson.Gson
import com.trynoice.api.client.apis.AccountApi
import com.trynoice.api.client.apis.CdnApi
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
import okhttp3.Cache
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.internal.userAgent
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.io.File
import java.util.*


/**
 * A thin wrapper around Retrofit to bundle together the networked API calls while transparently
 * managing authentication credentials.
 */
class NoiceApiClient(
  context: Context,
  gson: Gson,
  apiBaseUrl: String = "https://api.trynoice.com",
  cdnBaseUrl: String = "https://cdn.trynoice.com",
  userAgent: String = "noice-api-client",
) {

  private val credentialRepository = AuthCredentialRepository(context)
  private val signedInState = MutableStateFlow(credentialRepository.getRefreshToken() != null)
  private val refreshCredentialsMutex = Mutex()

  private val okhttpClient = OkHttpClient.Builder()
    .addNetworkInterceptor { chain ->
      // Intercept 'HTTP 204 - No Content' responses and rewrite them as HTTP 200 to avoid:
      // `kotlin.KotlinNullPointerException: Response from <method> was null but response body type
      // was declared as non-null.`
      // See: https://github.com/square/retrofit/issues/2867
      val response = chain.proceed(chain.request())
      if (response.code == 204) {
        HttpLoggingInterceptor.Logger.DEFAULT.log("rewriting HTTP 204 response as HTTP 200")
        response.newBuilder().code(200).build()
      } else {
        response
      }
    }
    .addNetworkInterceptor(HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    })
    .addInterceptor(AccessTokenInjector(credentialRepository, credentialRefresher = {
      runBlocking { refreshCredentials() }
    }))
    .addInterceptor(RefreshTokenInjector(credentialRepository))
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
      .baseUrl(apiBaseUrl)
      .addConverterFactory(
        GsonConverterFactory.create(
          gson.newBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date::class.java, EpochMillisToDateDeserializer())
            .create()
        )
      )
      .build()
  }

  private val accountApi: AccountApi by lazy { retrofit.create() }
  private val internalAccountApi: InternalAccountApi by lazy { retrofit.create() }
  private val subscriptionApi: SubscriptionApi by lazy { retrofit.create() }
  private val cdnApi: CdnApi by lazy {
    retrofit.newBuilder()
      .client(
        okhttpClient.newBuilder()
          .cache(Cache(File(context.cacheDir, "cdn-cache"), 256 * 1024 * 1024 /* 256 MB */))
          .build()
      )
      .baseUrl(cdnBaseUrl)
      .build()
      .create()
  }

  /**
   * Subscription management related APIs.
   */
  fun accounts() = accountApi

  /**
   * Account and user management related APIs.
   */
  fun subscriptions() = subscriptionApi

  /**
   * APIs to fetch resources from the CDN. The client caches CDN responses if and as directed by the
   * `Cache-Control` response headers.
   */
  fun cdn() = cdnApi

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
    try {
      internalAccountApi.signOut()
    } catch (e: HttpException) {
      // HTTP 401 = invalid or expired refresh token, thus a valid result.
      if (e.code() != 401) {
        throw e
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
