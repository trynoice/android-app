package com.github.ashutoshgngwr.noice.provider

import android.app.Activity
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionFlowParams
import com.trynoice.api.client.models.SubscriptionPlan

/**
 * [SubscriptionBillingProvider] implementation that provides subscriptions using Google Play as the
 * billing provider.
 */
class GooglePlaySubscriptionBillingProvider(
  private val apiClient: NoiceApiClient,
  private val billingProvider: InAppBillingProvider,
) : SubscriptionBillingProvider {

  override suspend fun getPlans(): List<SubscriptionPlan> {
    return apiClient.subscriptions().getPlans(SubscriptionPlan.PROVIDER_GOOGLE_PLAY)
  }

  override suspend fun launchBillingFlow(
    activity: Activity,
    plan: SubscriptionPlan,
    activeSubscription: Subscription?,
  ) {
    require(plan.provider == SubscriptionPlan.PROVIDER_GOOGLE_PLAY) {
      "google play provider launched billing flow for non-google-play subscription plan"
    }

    val sku = requireNotNull(plan.googlePlaySubscriptionId) {
      "subscription plan has null google play subscription id"
    }

    val activePurchaseToken = activeSubscription?.googlePlayPurchaseToken
    val subscription: Subscription = if (activePurchaseToken == null) {
      apiClient.subscriptions()
        .create(SubscriptionFlowParams(plan.id))
        .subscription
    } else {
      activeSubscription
    }

    val skuDetails = billingProvider.queryDetails(InAppBillingProvider.SkuType.SUBS, listOf(sku))
    billingProvider.purchase(
      activity,
      skuDetails.first(),
      oldPurchaseToken = activePurchaseToken,
      obfuscatedAccountId = subscription.id.toString(),
    )
  }
}
