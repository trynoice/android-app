package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.provider.StripeSubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.provider.SubscriptionBillingProvider
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionBillingProviderModule {

  @Provides
  @Singleton
  fun subscriptionProvider(
    apiClient: NoiceApiClient,
    appDispatchers: AppDispatchers,
  ): SubscriptionBillingProvider {
    return StripeSubscriptionBillingProvider(apiClient, appDispatchers)
  }
}
