package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.github.ashutoshgngwr.noice.provider.GooglePlaySubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.StripeSubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.provider.SubscriptionBillingProvider
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionBillingProviderModule {

  @Provides
  @Singleton
  fun subscriptionBillingProvider(
    @ApplicationContext context: Context,
    apiClient: NoiceApiClient,
    billingProvider: InAppBillingProvider,
  ): SubscriptionBillingProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return GooglePlaySubscriptionBillingProvider(apiClient, billingProvider)
    }

    return StripeSubscriptionBillingProvider(apiClient)
  }
}
