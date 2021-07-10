package com.github.ashutoshgngwr.noice

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CastAPIProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository

abstract class NoiceApplication : android.app.Application() {

  companion object {
    /**
     * Convenience method that returns [NoiceApplication] from the provided [context].
     */
    fun of(context: Context): NoiceApplication = context.applicationContext as NoiceApplication
  }

  private lateinit var castAPIProviderFactory: CastAPIProvider.Factory
  private lateinit var reviewFlowProvider: ReviewFlowProvider
  private lateinit var crashlyticsProvider: CrashlyticsProvider
  private lateinit var analyticsProvider: AnalyticsProvider
  private lateinit var settingsRepository: SettingsRepository

  override fun onCreate() {
    super.onCreate()
    settingsRepository = SettingsRepository.newInstance(this)
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
  fun setCastAPIProviderFactory(factory: CastAPIProvider.Factory) {
    castAPIProviderFactory = factory
  }

  fun getCastAPIProviderFactory(): CastAPIProvider.Factory = castAPIProviderFactory

  @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
  fun setReviewFlowProvider(provider: ReviewFlowProvider) {
    reviewFlowProvider = provider
  }

  fun getReviewFlowProvider(): ReviewFlowProvider = reviewFlowProvider

  @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
  fun setCrashlyticsProvider(provider: CrashlyticsProvider) {
    crashlyticsProvider = provider
    provider.setCollectionEnabled(settingsRepository.shouldShareUsageData())
  }

  fun getCrashlyticsProvider(): CrashlyticsProvider = crashlyticsProvider

  @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
  fun setAnalyticsProvider(provider: AnalyticsProvider) {
    analyticsProvider = provider
    provider.setCollectionEnabled(settingsRepository.shouldShareUsageData())
  }

  fun getAnalyticsProvider(): AnalyticsProvider = analyticsProvider
}
