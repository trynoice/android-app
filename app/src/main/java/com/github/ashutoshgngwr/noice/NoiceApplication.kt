package com.github.ashutoshgngwr.noice

import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.provider.CastAPIProvider

abstract class NoiceApplication : android.app.Application() {

  private lateinit var castAPIProviderFactory: CastAPIProvider.Factory

  @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
  fun setCastAPIProviderFactory(factory: CastAPIProvider.Factory) {
    castAPIProviderFactory = factory
  }

  fun getCastAPIProviderFactory() = castAPIProviderFactory
}
