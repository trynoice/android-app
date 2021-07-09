package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.PlaystoreCastAPIProvider

@Suppress("unused")
class PlaystoreNoiceApplication : NoiceApplication() {

  override fun onCreate() {
    super.onCreate()
    setCastAPIProviderFactory(PlaystoreCastAPIProvider.FACTORY)
  }
}
