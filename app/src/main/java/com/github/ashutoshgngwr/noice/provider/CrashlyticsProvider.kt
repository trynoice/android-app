package com.github.ashutoshgngwr.noice.provider

/**
 * [CrashlyticsProvider] is an abstract declaration of Firebase Crashlytics APIs used by the app.
 * This interface abstracts concrete implementations and thus allowing F-Droid flavored builds to be
 * compiled without adding the actual non-free GMS dependencies.
 */
interface CrashlyticsProvider {

  /**
   * Records a non-fatal report to send to Crashlytics.
   */
  fun recordException(e: Throwable)

  /**
   * Enables or disables the automatic data collection configuration for Crashlytics.
   */
  fun setCollectionEnabled(e: Boolean)

  /**
   * Logs a message that's included in the next fatal or non-fatal report.
   */
  fun log(m: String)
}
