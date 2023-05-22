package com.github.ashutoshgngwr.noice.metrics

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * [FirebaseCrashlyticsProvider] provides a real concrete implementation of Crashlytics API.
 */
class FirebaseCrashlyticsProvider : CrashlyticsProvider {

  private val fc = Firebase.crashlytics

  override fun recordException(e: Throwable) {
    fc.recordException(e)
  }

  override fun setCollectionEnabled(e: Boolean) {
    fc.setCrashlyticsCollectionEnabled(e)
  }

  override fun log(m: String) {
    fc.log(m)
  }
}
