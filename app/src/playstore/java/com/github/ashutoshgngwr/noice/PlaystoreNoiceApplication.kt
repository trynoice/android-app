package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.RealCastAPIProvider
import com.github.ashutoshgngwr.noice.provider.PlaystoreReviewFlowProvider

@Suppress("unused")
class PlaystoreNoiceApplication : NoiceApplication() {

  override fun onCreate() {
    super.onCreate()
    setCastAPIProviderFactory(RealCastAPIProvider.FACTORY)
    setReviewFlowProvider(PlaystoreReviewFlowProvider)
  }
}
