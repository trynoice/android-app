package com.trynoice.api.client.interceptors

import com.trynoice.api.client.annotations.InjectAccessToken
import com.trynoice.api.client.interceptors.AccessTokenInjector.AccessTokenProvider
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly

/**
 * An interceptor for injecting the Authorization header with an access token to the requests
 * annotated with [InjectAccessToken] annotation. If the access token is missing or expired (`HTTP
 * 401`), it invokes [AccessTokenProvider.get] with `refresh=true` to refresh API Client's
 * credentials before providing the access token. It then reattempts the request with/without the
 * new access token and returns its response.
 */
internal class AccessTokenInjector(
  private val accessTokenProvider: AccessTokenProvider,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    if (!chain.request().isAnnotationPresent(InjectAccessToken::class)) {
      return chain.proceed(chain.request())
    }

    val accessToken = accessTokenProvider.get(false)
    if (accessToken != null) {
      val response = performRequest(chain, accessToken)
      if (response.code != 401) {
        return response
      }
      response.closeQuietly() // required when it is a streaming response.
    }

    // access token is either missing or expired. Attempt to refresh credentials and retry the
    // request with a best-effort strategy.
    return performRequest(chain, accessTokenProvider.get(true))
  }

  private fun performRequest(chain: Interceptor.Chain, accessToken: String?): Response {
    val requestBuilder = chain.request().newBuilder()
    if (accessToken != null) {
      requestBuilder.addHeader("Authorization", "Bearer $accessToken")
    }

    return chain.proceed(requestBuilder.build())
  }

  fun interface AccessTokenProvider {
    /**
     * Provides the access token to be injected. If [refresh] is `true`, the credentials must be
     * refreshed before providing the access token.
     */
    fun get(refresh: Boolean): String?
  }
}
