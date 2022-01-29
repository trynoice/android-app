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
import com.github.ashutoshgngwr.noice.provider.InAppBillingDonateViewProvider
import com.github.ashutoshgngwr.noice.provider.OpenCollectiveDonateViewProvider
import com.github.ashutoshgngwr.noice.provider.PlaystoreReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.RealAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.RealBillingProvider
import com.github.ashutoshgngwr.noice.provider.RealCastApiProvider
import com.github.ashutoshgngwr.noice.provider.RealCrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt [Module] to provide dependencies for full build variant.
 */
@Module
@InstallIn(SingletonComponent::class)
object FullModule {

  private fun isGoogleMobileServiceAvailable(context: Context): Boolean {
    return GoogleApiAvailability.getInstance()
      .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
  }

  @Provides
  @Singleton
  fun castApiProvider(@ApplicationContext context: Context): CastApiProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return RealCastApiProvider(context)
    }

    return DummyCastApiProvider
  }

  @Provides
  @Singleton
  fun reviewFlowProvider(@ApplicationContext context: Context): ReviewFlowProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return PlaystoreReviewFlowProvider
    }

    return GitHubReviewFlowProvider
  }

  @Provides
  @Singleton
  fun crashlyticsProvider(): CrashlyticsProvider = RealCrashlyticsProvider

  @Provides
  @Singleton
  fun analyticsProvider(): AnalyticsProvider = RealAnalyticsProvider

  @Provides
  @Singleton
  fun billingProvider(@ApplicationContext context: Context): BillingProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return RealBillingProvider
    }

    return DummyBillingProvider
  }

  @Provides
  @Singleton
  fun donateViewProvider(@ApplicationContext context: Context): DonateViewProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return InAppBillingDonateViewProvider
    }

    return OpenCollectiveDonateViewProvider
  }
}
