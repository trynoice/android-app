package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.PlaystoreReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.RealAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.RealCastAPIProvider
import com.github.ashutoshgngwr.noice.provider.RealCrashlyticsProvider
import com.google.firebase.FirebaseApp

@Suppress("unused")
class PlaystoreNoiceApplication : NoiceApplication() {

  override fun onCreate() {
    super.onCreate()
    FirebaseApp.initializeApp(this)
    setCastAPIProviderFactory(RealCastAPIProvider.FACTORY)
    setReviewFlowProvider(PlaystoreReviewFlowProvider)
    setCrashlyticsProvider(RealCrashlyticsProvider)
    setAnalyticsProvider(RealAnalyticsProvider)
  }
}
