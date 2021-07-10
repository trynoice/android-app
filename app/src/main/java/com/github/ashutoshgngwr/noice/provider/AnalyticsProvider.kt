package com.github.ashutoshgngwr.noice.provider

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
}
