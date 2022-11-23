package com.trynoice.api.client.interceptors

import androidx.core.os.LocaleListCompat
import com.trynoice.api.client.annotations.InjectAcceptLanguageHeader
import okhttp3.Interceptor
import okhttp3.Response

/**
 * An interceptor that injects `Accept-Language` header in requests annotated with
 * [InjectAcceptLanguageHeader] annotation.
 */
class AcceptLanguageHeaderInjector : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    return chain.proceed(
      if (!chain.request().isAnnotationPresent(InjectAcceptLanguageHeader::class))
        chain.request()
      else
        chain.request()
          .newBuilder()
          .header("Accept-Language", LocaleListCompat.getAdjustedDefault().toLanguageTags())
          .build()
    )
  }
}
