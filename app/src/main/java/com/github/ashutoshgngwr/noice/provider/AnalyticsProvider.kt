package com.github.ashutoshgngwr.noice.provider

import android.os.Bundle
import androidx.core.os.bundleOf
import kotlin.reflect.KClass

/**
 * [AnalyticsProvider] is an abstract declaration of Firebase Analytics APIs used by the app.
 * This interface abstracts concrete implementations and thus allowing F-Droid flavored builds to be
 * compiled without adding the actual non-free GMS dependencies.
 */
interface AnalyticsProvider {

  /**
   * Sets whether analytics collection is enabled for this app on this device. This setting is
   * persisted across app sessions.
   */
  fun setCollectionEnabled(e: Boolean)

  /**
   * Logs a new event with provided [name] and [params].
   */
  fun logEvent(name: String, params: Bundle)

  /**
   * Logs a screen view event.
   */
  fun setCurrentScreen(name: String, clazz: KClass<out Any>, params: Bundle = bundleOf())

  /**
   * Logs a player stop event.
   */
  fun logPlayerStartEvent(key: String)

  /**
   * Logs a player start event.
   */
  fun logPlayerStopEvent(key: String)

  /**
   * Logs the time when cast session is started.
   */
  fun logCastSessionStartEvent()

  /**
   * Logs the time when cast session ends.
   */
  fun logCastSessionEndEvent()
}
