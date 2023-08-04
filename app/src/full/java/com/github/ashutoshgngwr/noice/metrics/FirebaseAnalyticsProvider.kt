package com.github.ashutoshgngwr.noice.metrics

import android.os.Bundle
import androidx.core.os.bundleOf
import com.github.ashutoshgngwr.noice.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.setConsent
import com.google.firebase.ktx.Firebase
import kotlin.reflect.KClass

/**
 * [FirebaseAnalyticsProvider] provides a real concrete implementation of the Firebase Analytics API.
 */
class FirebaseAnalyticsProvider : AnalyticsProvider {

  private val fa = Firebase.analytics

  init {
    fa.setConsent {
      adStorage = FirebaseAnalytics.ConsentStatus.DENIED
      analyticsStorage = FirebaseAnalytics.ConsentStatus.GRANTED
    }

    fa.setDefaultEventParameters(
      bundleOf("app_version_code" to BuildConfig.VERSION_CODE)
    )
  }

  override fun setCollectionEnabled(e: Boolean) {
    fa.setAnalyticsCollectionEnabled(e)
  }

  override fun logEvent(name: String, params: Bundle) {
    fa.logEvent(name, params)
  }

  override fun setCurrentScreen(clazz: KClass<out Any>) {
    clazz.simpleName?.also { className ->
      val screenName = className.removeSuffix("Activity").removeSuffix("Fragment")
      fa.logEvent(
        FirebaseAnalytics.Event.SCREEN_VIEW, bundleOf(
          FirebaseAnalytics.Param.SCREEN_NAME to screenName,
          FirebaseAnalytics.Param.SCREEN_CLASS to clazz.simpleName,
        )
      )
    }
  }
}
