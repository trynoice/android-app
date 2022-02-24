package com.github.ashutoshgngwr.noice.provider

import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SubscriptionPlan

/**
 * An abstraction layer to allow application to select from Stripe and Google Play subscription
 * providers based on the build variant and Google Play billing service availability.
 */
interface SubscriptionProvider {

  /**
   * @return a list of subscription plans offered by the given [SubscriptionProvider].
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  suspend fun getPlans(): List<SubscriptionPlan>
}

/**
 * [SubscriptionProvider] implementation that provides subscriptions using Stripe as the billing
 * provider.
 */
class StripeSubscriptionProvider(private val apiClient: NoiceApiClient) : SubscriptionProvider {

  /**
   * @return a list of subscription plans offered by the given [SubscriptionProvider].
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  override suspend fun getPlans(): List<SubscriptionPlan> {
    return apiClient.subscriptions().getPlans("stripe")
  }
}
