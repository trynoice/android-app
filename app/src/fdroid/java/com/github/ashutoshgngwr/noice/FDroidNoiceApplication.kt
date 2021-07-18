package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.DummyAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.DummyBillingProvider
import com.github.ashutoshgngwr.noice.provider.DummyCastAPIProvider
import com.github.ashutoshgngwr.noice.provider.DummyCrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.GitHubReviewFlowProvider

@Suppress("unused")
class FDroidNoiceApplication : NoiceApplication() {

  override fun onCreate() {
    super.onCreate()
    setCastAPIProviderFactory(DummyCastAPIProvider.FACTORY)
    setReviewFlowProvider(GitHubReviewFlowProvider)
    setCrashlyticsProvider(DummyCrashlyticsProvider)
    setAnalyticsProvider(DummyAnalyticsProvider)
    setBillingProvider(DummyBillingProvider)
  }
}
