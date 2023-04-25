package com.github.ashutoshgngwr.noice.metrics

import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.setConsent
import com.google.firebase.ktx.Firebase
import kotlin.reflect.KClass

/**
 * [RealAnalyticsProvider] provides a real concrete implementation of the Firebase Analytics API.
 */
object RealAnalyticsProvider : AnalyticsProvider {

  private val fa = Firebase.analytics
  private val playerStartTimes = mutableMapOf<String, Long>()

  private var playbackStartTime = -1L
  private var castSessionStartTime = -1L

  init {
    fa.setConsent {
      adStorage = FirebaseAnalytics.ConsentStatus.DENIED
      analyticsStorage = FirebaseAnalytics.ConsentStatus.GRANTED
    }
  }

  override fun setCollectionEnabled(e: Boolean) {
    fa.setAnalyticsCollectionEnabled(e)
  }

  override fun setCurrentScreen(name: String, clazz: KClass<out Any>, params: Bundle) {
    params.putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
    clazz.simpleName?.also {
      params.putString(FirebaseAnalytics.Param.SCREEN_CLASS, it)
    }

    fa.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
  }


  override fun logEvent(name: String, params: Bundle) {
    fa.logEvent(name, params)
  }

  override fun logPlayerStartEvent(key: String) {
    if (key !in playerStartTimes) {
      playerStartTimes[key] = System.currentTimeMillis()
    }

    if (playbackStartTime <= 0) {
      playbackStartTime = System.currentTimeMillis()
    }
  }

  override fun logPlayerStopEvent(key: String) {
    playerStartTimes.remove(key)?.also {
      val params = bundleOf("sound_key" to key, "duration_ms" to System.currentTimeMillis() - it)
      fa.logEvent("sound_session", params)
    }

    if (playerStartTimes.isEmpty() && playbackStartTime > 0) {
      val duration = System.currentTimeMillis() - playbackStartTime
      fa.logEvent("playback_session", bundleOf("duration_ms" to duration))
      playbackStartTime = -1L
    }
  }

  override fun logCastSessionStartEvent() {
    castSessionStartTime = System.currentTimeMillis()
  }

  override fun logCastSessionEndEvent() {
    if (castSessionStartTime <= 0) {
      return
    }

    val params = bundleOf("duration_ms" to System.currentTimeMillis() - castSessionStartTime)
    fa.logEvent("cast_session", params)
    castSessionStartTime = -1
  }
}
