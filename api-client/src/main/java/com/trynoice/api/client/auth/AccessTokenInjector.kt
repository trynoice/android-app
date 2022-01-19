package com.trynoice.api.client.auth

import com.trynoice.api.client.auth.annotations.NeedsAccessToken
import com.trynoice.api.client.ext.isAnnotationPresent
import okhttp3.Interceptor
import okhttp3.Response

/**
 * An interceptor for injecting the Authorization header with an access token to the requests
 * annotated with [NeedsAccessToken] annotation. If the access token is missing or expired (`HTTP
 * 401`), it invokes [credentialRefresher] to refresh API Client's credentials. It then reattempts
 * the request with/without the new access token and returns its response.
 */
internal class AccessTokenInjector(
  private val credentialRepository: AuthCredentialRepository,
  private val credentialRefresher: CredentialRefresher,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    if (!chain.request().isAnnotationPresent(NeedsAccessToken::class)) {
      return chain.proceed(chain.request())
    }

    val accessToken = credentialRepository.getAccessToken()
    if (accessToken != null) {
      val response = performRequest(chain, accessToken)
      if (response.code != 401) {
        return response
      }
    }

    // access token is either missing or expired. Attempt to refresh credentials and retry the
    // request with a best-effort strategy.
    credentialRefresher.refresh()
    return performRequest(chain, credentialRepository.getAccessToken())
  }

  private fun performRequest(chain: Interceptor.Chain, accessToken: String?): Response {
    val requestBuilder = chain.request().newBuilder()
    if (accessToken != null) {
      requestBuilder.addHeader("Authorization", "Bearer $accessToken")
    }

    return chain.proceed(requestBuilder.build())
  }

  /**
   * [AccessTokenInjector] uses it to notify the API Client that it should request for new
   * credentials since the current access token may have expired.
   */
  fun interface CredentialRefresher {
    fun refresh()
  }
}
