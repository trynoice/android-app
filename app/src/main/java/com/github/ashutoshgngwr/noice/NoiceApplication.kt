package com.github.ashutoshgngwr.noice

import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.provider.CastAPIProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider

abstract class NoiceApplication : android.app.Application() {

  private lateinit var castAPIProviderFactory: CastAPIProvider.Factory
  private lateinit var reviewFlowProvider: ReviewFlowProvider

  @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
  fun setCastAPIProviderFactory(factory: CastAPIProvider.Factory) {
    castAPIProviderFactory = factory
  }

  fun getCastAPIProviderFactory() = castAPIProviderFactory


  @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
  fun setReviewFlowProvider(provider: ReviewFlowProvider) {
    reviewFlowProvider = provider
  }

  fun getReviewFlowProvider() = reviewFlowProvider
}
