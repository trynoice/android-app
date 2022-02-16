package com.trynoice.api.client.auth.annotations

/**
 * When applied to a Retrofit interface method, the API Client automatically adds an Authorization
 * header with the access token to the request.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
internal annotation class NeedsAccessToken
