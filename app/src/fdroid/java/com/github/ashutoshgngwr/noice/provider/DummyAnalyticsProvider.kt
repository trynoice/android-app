package com.github.ashutoshgngwr.noice.provider

import android.os.Bundle
import kotlin.reflect.KClass

/**
 * [DummyAnalyticsProvider] provides a no-op analytics API for F-Droid flavored builds to compile
 * without non-free dependencies.
 */
object DummyAnalyticsProvider : AnalyticsProvider {
  override fun setCollectionEnabled(e: Boolean) = Unit
  override fun logEvent(name: String, params: Bundle) = Unit
  override fun setCurrentScreen(name: String, clazz: KClass<out Any>, params: Bundle) = Unit
  override fun logPlayerStartEvent(key: String) = Unit
  override fun logPlayerStopEvent(key: String) = Unit
  override fun logCastSessionStartEvent() = Unit
  override fun logCastSessionEndEvent() = Unit
}
