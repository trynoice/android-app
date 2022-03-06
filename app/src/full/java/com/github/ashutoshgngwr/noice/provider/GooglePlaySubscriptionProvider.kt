package com.github.ashutoshgngwr.noice.provider

import android.app.Activity
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionFlowParams
import com.trynoice.api.client.models.SubscriptionPlan

/**
 * [SubscriptionProvider] implementation that provides subscriptions using Google Play as the
 * billing provider.
 */
class GooglePlaySubscriptionProvider(
  private val apiClient: NoiceApiClient,
  private val billingProvider: InAppBillingProvider,
) : SubscriptionProvider {

  override suspend fun getPlans(): List<SubscriptionPlan> {
    return apiClient.subscriptions().getPlans(SubscriptionPlan.PROVIDER_GOOGLE_PLAY)
  }

  override suspend fun launchBillingFlow(activity: Activity, plan: SubscriptionPlan) {
    require(plan.provider == SubscriptionPlan.PROVIDER_GOOGLE_PLAY) {
      "google play provider launched billing flow for non-google-play subscription plan"
    }

    val sku = requireNotNull(plan.googlePlaySubscriptionId) {
      "subscription plan has null google play subscription id"
    }

    val skuDetails = billingProvider.queryDetails(InAppBillingProvider.SkuType.SUBS, listOf(sku))
    val result = apiClient.subscriptions().create(SubscriptionFlowParams(plan.id))
    billingProvider.purchase(activity, skuDetails.first(), result.subscription.id.toString())
  }

  override suspend fun getSubscription(subscriptionId: Long): Subscription {
    return apiClient.subscriptions().get(subscriptionId)
  }
}
