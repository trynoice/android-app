package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.DonationFragmentProvider
import com.github.ashutoshgngwr.noice.provider.DummyAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.DummyCastApiProvider
import com.github.ashutoshgngwr.noice.provider.DummyCrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.DummyInAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.GitHubReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.OpenCollectiveDonationFragmentProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.StripeSubscriptionProvider
import com.github.ashutoshgngwr.noice.provider.SubscriptionProvider
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


// Hilt modules to create providers for the free build variant.

@Module
@InstallIn(SingletonComponent::class)
object CastApiProviderModule {
  @Provides
  @Singleton
  fun castApiProvider(): CastApiProvider = DummyCastApiProvider
}

@Module
@InstallIn(SingletonComponent::class)
object ReviewFlowProviderModule {
  @Provides
  @Singleton
  fun reviewFlowProvider(): ReviewFlowProvider = GitHubReviewFlowProvider
}

@Module
@InstallIn(SingletonComponent::class)
object CrashlyticsProviderModule {
  @Provides
  @Singleton
  fun crashlyticsProvider(): CrashlyticsProvider = DummyCrashlyticsProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsProviderModule {
  @Provides
  @Singleton
  fun analyticsProvider(): AnalyticsProvider = DummyAnalyticsProvider
}

@Module
@InstallIn(SingletonComponent::class)
object InAppBillingProviderModule {
  @Provides
  @Singleton
  fun inAppBillingProvider(): InAppBillingProvider = DummyInAppBillingProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DonationFragmentProviderModule {
  @Provides
  @Singleton
  fun donationFragmentProvider(): DonationFragmentProvider = OpenCollectiveDonationFragmentProvider
}

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionProviderModule {
  @Provides
  @Singleton
  fun subscriptionProvider(apiClient: NoiceApiClient): SubscriptionProvider {
    return StripeSubscriptionProvider(apiClient)
  }
}
