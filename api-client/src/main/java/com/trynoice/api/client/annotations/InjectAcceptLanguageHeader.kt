package com.trynoice.api.client.annotations

/**
 * When applied to a Retrofit interface method, the API Client adds the Accept-Language header
 * according to device's configured locales to the request.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class InjectAcceptLanguageHeader
