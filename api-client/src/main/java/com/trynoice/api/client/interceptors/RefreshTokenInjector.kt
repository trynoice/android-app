package com.trynoice.api.client.interceptors

import com.trynoice.api.client.annotations.InjectRefreshToken
import okhttp3.Interceptor
import okhttp3.Response

/**
 * An interceptor for injecting `X-Refresh-Token` header with a refresh token to the requests
 * annotated with [InjectRefreshToken] annotation.
 */
internal class RefreshTokenInjector(
  private val refreshTokenProvider: RefreshTokenProvider,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val injectRefreshToken = chain.request().isAnnotationPresent(InjectRefreshToken::class)
    val refreshToken = refreshTokenProvider.get()
    if (!injectRefreshToken || refreshToken == null) {
      return chain.proceed(chain.request())
    }

    return chain.proceed(
      chain.request()
        .newBuilder()
        .header("X-Refresh-Token", refreshToken)
        .build()
    )
  }

  fun interface RefreshTokenProvider {
    fun get(): String?
  }
}
