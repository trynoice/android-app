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

    // we have set-up the Google Play subscription products such that each subscription has a single
    // base plan with at-most two offers (including the base plan without an offer). It may have
    // multiple pricing phases and the best way to find the the correct offer is to look for an
    // offer with maximum number of pricing phases. The offer with most number of pricing phases
    // should be the most economical offer.
    val details = billingProvider.queryDetails(InAppBillingProvider.ProductType.SUBS, listOf(sku))
    val offerToken = details.first()
      .subscriptionOfferDetails
      ?.maxByOrNull { it.pricingPhases.size }
      ?.offerToken

    billingProvider.purchase(
      activity,
      details.first(),
      subscriptionOfferToken = offerToken,
      oldPurchaseToken = activePurchaseToken,
      obfuscatedAccountId = subscription.id.toString(),
    )
  }

  override fun canUpgrade(s: Subscription): Boolean {
    return s.isActive && s.plan.provider == SubscriptionPlan.PROVIDER_GOOGLE_PLAY
  }
}
