package com.github.ashutoshgngwr.noice.provider

import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SubscriptionPlan

/**
 * [SubscriptionProvider] implementation that provides subscriptions using Google Play as the
 * billing provider.
 */
class GooglePlaySubscriptionProvider(private val apiClient: NoiceApiClient) : SubscriptionProvider {

  /**
   * @return a list of subscription plans offered by the given [SubscriptionProvider].
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  override suspend fun getPlans(): List<SubscriptionPlan> {
    return apiClient.subscriptions().getPlans("google_play")
  }
}
