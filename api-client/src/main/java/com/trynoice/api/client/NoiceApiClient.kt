package com.trynoice.api.client

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.GsonBuilder
import com.trynoice.api.client.apis.AccountApi
import com.trynoice.api.client.apis.InternalAccountApi
import com.trynoice.api.client.apis.SubscriptionApi
import com.trynoice.api.client.auth.AccessTokenInjector
import com.trynoice.api.client.auth.AuthCredentialRepository
import com.trynoice.api.client.auth.RefreshTokenInjector
import com.trynoice.api.client.models.AuthCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import java.util.Collections.synchronizedSet

private val LOG_TAG = NoiceApiClient::class.simpleName

/**
 * A thin wrapper around Retrofit to bundle together the networked API calls while transparently
 * managing authentication credentials.
 */
class NoiceApiClient(
  context: Context,
  baseUrl: String = "https://api.trynoice.com",
) {

  private val signInStateListeners = synchronizedSet(mutableSetOf<SignInStateListener>())
  private val credentialRepository = AuthCredentialRepository(context)
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
        GsonConverterFactory.create(
          GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create()
        )
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
   * Adds a new listener to listen for sign-in state changes. The listeners are always invoked on
   * application's main thread.
   */
  fun addSignInStateListener(listener: SignInStateListener) {
    signInStateListeners.add(listener)
  }

  /**
   * Removes a registered listener. Does a no-op if the listener wasn't registered.
   */
  fun removeSignInStateListener(listener: SignInStateListener) {
    signInStateListeners.remove(listener)
  }

  /**
   * Registers a new listener that is automatically added and removed based on the lifecycle of its
   * [owner].
   */
  fun registerSignInStateListener(owner: LifecycleOwner, listener: SignInStateListener) {
    owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onCreate(owner: LifecycleOwner) {
        addSignInStateListener(listener)
      }

      override fun onDestroy(owner: LifecycleOwner) {
        removeSignInStateListener(listener)
        owner.lifecycle.removeObserver(this)
      }
    })
  }

  /**
   * Adds the sign-in token to the credential store and then attempts to issue new credentials using
   * it.
   */
  suspend fun signInWithToken(signInToken: String) {
    credentialRepository.setCredentials(AuthCredentials(signInToken, ""))
    refreshCredentials()
    notifySignInStateListeners()
  }

  /**
   * Weak check to know if the API client has a (valid or invalid) refresh token.
   */
  fun isSignedIn() = credentialRepository.getRefreshToken() != null

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
    notifySignInStateListeners()
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
            notifySignInStateListeners()
          } else {
            Log.w(LOG_TAG, "refresh credential request failed", it)
          }
        }
    }
  }

  private suspend fun notifySignInStateListeners() = coroutineScope {
    val isSignedIn = credentialRepository.getRefreshToken() != null
    signInStateListeners.forEach {
      launch(Dispatchers.Main) { it.onSignInStateChanged(isSignedIn) }
    }
  }

  /**
   * Listener to listen for sign-in state changes.
   */
  fun interface SignInStateListener {

    /**
     * Invoked when the sign-in state has changed.
     *
     * @param isSignedIn indicates whether the user is currently signed-in or not.
     */
    fun onSignInStateChanged(isSignedIn: Boolean)
  }
}
