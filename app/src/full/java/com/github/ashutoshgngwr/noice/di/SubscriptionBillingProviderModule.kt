package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.billing.GooglePlaySubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.billing.InAppBillingProvider
import com.github.ashutoshgngwr.noice.billing.StripeSubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.billing.SubscriptionBillingProvider
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
    appDispatchers: AppDispatchers,
  ): SubscriptionBillingProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return GooglePlaySubscriptionBillingProvider(apiClient, billingProvider)
    }

    return StripeSubscriptionBillingProvider(apiClient, appDispatchers)
  }
}
