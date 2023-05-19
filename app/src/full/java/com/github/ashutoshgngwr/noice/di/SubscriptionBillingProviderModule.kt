package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.billing.GooglePlayBillingProvider
import com.github.ashutoshgngwr.noice.billing.GooglePlaySubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.billing.StripeSubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.billing.SubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionBillingProviderModule {

  @Provides
  @Singleton
  fun subscriptionBillingProvider(
    apiClient: NoiceApiClient,
    billingProvider: GooglePlayBillingProvider?,
    appDb: AppDatabase,
    @AppCoroutineScope appScope: CoroutineScope,
    appDispatchers: AppDispatchers,
    stripeSubscriptionBillingProvider: StripeSubscriptionBillingProvider,
  ): SubscriptionBillingProvider {
    if (billingProvider != null) {
      return GooglePlaySubscriptionBillingProvider(
        apiClient = apiClient,
        billingProvider = billingProvider,
        appDb = appDb,
        defaultScope = appScope,
        appDispatchers = appDispatchers,
      )
    }

    return stripeSubscriptionBillingProvider
  }
}
