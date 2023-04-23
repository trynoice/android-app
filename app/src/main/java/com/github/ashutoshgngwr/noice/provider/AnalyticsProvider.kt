package com.github.ashutoshgngwr.noice.provider

import android.os.Bundle
import androidx.core.os.bundleOf
import kotlin.reflect.KClass

/**
 * [AnalyticsProvider] is an abstract declaration of Firebase Analytics APIs used by the app.
 * This interface abstracts concrete implementations and thus allowing free flavored builds to be
 * compiled without adding the actual non-free GMS dependencies.
 */
interface AnalyticsProvider {

  /**
   * Sets whether analytics collection is enabled for this app on this device. This setting persists
   * across app sessions.
   */
  fun setCollectionEnabled(e: Boolean)

  /**
   * Logs a new event with provided [name] and [params].
   */
  fun logEvent(name: String, params: Bundle = bundleOf())

  /**
   * Logs a screen view event.
   */
  fun setCurrentScreen(clazz: KClass<out Any>)
}

/**
 * A no-op analytics provider for free build variant where non-free dependencies are not allowed.
 */
object DummyAnalyticsProvider : AnalyticsProvider {
  override fun setCollectionEnabled(e: Boolean) = Unit
  override fun logEvent(name: String, params: Bundle) = Unit
  override fun setCurrentScreen(clazz: KClass<out Any>) = Unit
}
