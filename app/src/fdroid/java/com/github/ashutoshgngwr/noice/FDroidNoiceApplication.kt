package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.DummyCastAPIProvider
import com.github.ashutoshgngwr.noice.provider.GitHubReviewFlowProvider

@Suppress("unused")
class FDroidNoiceApplication : NoiceApplication() {

  override fun onCreate() {
    super.onCreate()
    setCastAPIProviderFactory(DummyCastAPIProvider.FACTORY)
    setReviewFlowProvider(GitHubReviewFlowProvider)
  }
}
