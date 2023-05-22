package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.billing.StripeSubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.billing.SubscriptionBillingProvider
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
  fun subscriptionBillingProvider(stripeSubscriptionBillingProvider: StripeSubscriptionBillingProvider): SubscriptionBillingProvider {
    return stripeSubscriptionBillingProvider
  }
}
