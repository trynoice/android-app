package com.trynoice.api.client.interceptors

import okhttp3.Request
import retrofit2.Invocation
import kotlin.reflect.KClass

/**
 * Helper to check if an OkHttp [Request] contains the given [annotation].
 */
internal fun Request.isAnnotationPresent(annotation: KClass<out Annotation>): Boolean {
  return tag(Invocation::class.java)
    ?.method()
    ?.isAnnotationPresent(annotation.java) ?: false
}
