package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.DonateViewProvider
import com.github.ashutoshgngwr.noice.provider.DummyAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.DummyBillingProvider
import com.github.ashutoshgngwr.noice.provider.DummyCastApiProvider
import com.github.ashutoshgngwr.noice.provider.DummyCrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.GitHubReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.OpenCollectiveDonateViewProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt [Module] to provide dependencies for free build variant.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

  @Provides
  @Singleton
  fun castApiProvider(): CastApiProvider = DummyCastApiProvider

  @Provides
  @Singleton
  fun reviewFlowProvider(): ReviewFlowProvider = GitHubReviewFlowProvider

  @Provides
  @Singleton
  fun crashlyticsProvider(): CrashlyticsProvider = DummyCrashlyticsProvider

  @Provides
  @Singleton
  fun analyticsProvider(): AnalyticsProvider = DummyAnalyticsProvider

  @Provides
  @Singleton
  fun billingProvider(): BillingProvider = DummyBillingProvider

  @Provides
  @Singleton
  fun donateViewProvider(): DonateViewProvider = OpenCollectiveDonateViewProvider
}
