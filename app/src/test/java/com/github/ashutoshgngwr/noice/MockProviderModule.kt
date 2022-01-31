package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.DonateViewProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ProviderModule::class])
object MockProviderModule {

  @Provides
  @Singleton
  fun castApiProvider(): CastApiProvider = mockk(relaxed = true)

  @Provides
  @Singleton
  fun reviewFlowProvider(): ReviewFlowProvider = mockk(relaxed = true)

  @Provides
  @Singleton
  fun crashlyticsProvider(): CrashlyticsProvider = mockk(relaxed = true)

  @Provides
  @Singleton
  fun analyticsProvider(): AnalyticsProvider = mockk(relaxed = true)

  @Provides
  @Singleton
  fun billingProvider(): BillingProvider = mockk(relaxed = true)

  @Provides
  @Singleton
  fun donateViewProvider(): DonateViewProvider = mockk(relaxed = true)
}
