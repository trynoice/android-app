package com.trynoice.api.client.auth

import com.trynoice.api.client.auth.annotations.NeedsRefreshToken
import com.trynoice.api.client.ext.isAnnotationPresent
import okhttp3.Interceptor
import okhttp3.Response

/**
 * An interceptor for injecting `X-Refresh-Token` header with a refresh token to the requests
 * annotated with [NeedsRefreshToken] annotation.
 */
internal class RefreshTokenInjector(
  private val credentialRepository: AuthCredentialRepository,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val needsRefreshToken = chain.request().isAnnotationPresent(NeedsRefreshToken::class)
    val refreshToken = credentialRepository.getRefreshToken()
    if (!needsRefreshToken || refreshToken == null) {
      return chain.proceed(chain.request())
    }

    return chain.proceed(
      chain.request()
        .newBuilder()
        .header("X-Refresh-Token", refreshToken)
        .build()
    )
  }
}
