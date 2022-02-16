package com.trynoice.api.client.auth.annotations

/**
 * When applied to a Retrofit interface method, the API Client automatically adds the
 * `X-Refresh-Token` header with the refresh token to the request.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
internal annotation class NeedsRefreshToken
