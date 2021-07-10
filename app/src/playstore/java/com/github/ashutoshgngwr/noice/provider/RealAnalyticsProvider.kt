package com.github.ashutoshgngwr.noice.provider

import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * [RealAnalyticsProvider] provides a real concrete implementation of the Firebase Analytics API.
 */
object RealAnalyticsProvider : AnalyticsProvider {

  private val fa = Firebase.analytics

  override fun setCollectionEnabled(e: Boolean) {
    fa.setAnalyticsCollectionEnabled(e)
  }
}
