package com.github.ashutoshgngwr.noice.provider

/**
 * [DummyCrashlyticsProvider] provides a no-op crashlytics API for F-Droid flavored builds to
 * compile without non-free dependencies.
 */
object DummyCrashlyticsProvider : CrashlyticsProvider {
  override fun recordException(e: Throwable) = Unit
  override fun setCollectionEnabled(e: Boolean) = Unit
  override fun log(m: String) = Unit
}
