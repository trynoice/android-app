package com.github.ashutoshgngwr.noice

import android.content.Context
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.DonateViewProvider
import com.github.ashutoshgngwr.noice.provider.DummyBillingProvider
import com.github.ashutoshgngwr.noice.provider.DummyCastApiProvider
import com.github.ashutoshgngwr.noice.provider.GitHubReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.GooglePlaySubscriptionProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingDonateViewProvider
import com.github.ashutoshgngwr.noice.provider.OpenCollectiveDonateViewProvider
import com.github.ashutoshgngwr.noice.provider.PlaystoreReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.RealAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.RealBillingProvider
import com.github.ashutoshgngwr.noice.provider.RealCastApiProvider
import com.github.ashutoshgngwr.noice.provider.RealCrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.StripeSubscriptionProvider
import com.github.ashutoshgngwr.noice.provider.SubscriptionProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


// Hilt modules to create providers for the full build variant.

private fun isGoogleMobileServiceAvailable(context: Context): Boolean {
  return GoogleApiAvailability.getInstance()
    .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

@Module
@InstallIn(SingletonComponent::class)
object CastApiProviderModule {
  @Provides
  @Singleton
  fun castApiProvider(@ApplicationContext context: Context): CastApiProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return RealCastApiProvider(context)
    }

    return DummyCastApiProvider
  }
}

@Module
@InstallIn(SingletonComponent::class)
object ReviewFlowProviderModule {
  @Provides
  @Singleton
  fun reviewFlowProvider(@ApplicationContext context: Context): ReviewFlowProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return PlaystoreReviewFlowProvider
    }

    return GitHubReviewFlowProvider
  }
}

@Module
@InstallIn(SingletonComponent::class)
object CrashlyticsProviderModule {
  @Provides
  @Singleton
  fun crashlyticsProvider(): CrashlyticsProvider = RealCrashlyticsProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsProviderModule {
  @Provides
  @Singleton
  fun analyticsProvider(): AnalyticsProvider = RealAnalyticsProvider
}

@Module
@InstallIn(SingletonComponent::class)
object BillingProviderModule {
  @Provides
  @Singleton
  fun billingProvider(@ApplicationContext context: Context): BillingProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return RealBillingProvider
    }

    return DummyBillingProvider
  }
}

@Module
@InstallIn(SingletonComponent::class)
object DonateViewProviderModule {
  @Provides
  @Singleton
  fun donateViewProvider(@ApplicationContext context: Context): DonateViewProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return InAppBillingDonateViewProvider
    }

    return OpenCollectiveDonateViewProvider
  }
}

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionProviderModule {
  @Provides
  @Singleton
  fun subscriptionProvider(
    @ApplicationContext context: Context,
    apiClient: NoiceApiClient,
  ): SubscriptionProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return GooglePlaySubscriptionProvider(apiClient)
    }

    return StripeSubscriptionProvider(apiClient)
  }
}
