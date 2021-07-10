package com.github.ashutoshgngwr.noice.provider

/**
 * [DummyAnalyticsProvider] provides a no-op analytics API for F-Droid flavored builds to compile
 * without non-free dependencies.
 */
object DummyAnalyticsProvider : AnalyticsProvider {
  override fun setCollectionEnabled(e: Boolean) = Unit
}
