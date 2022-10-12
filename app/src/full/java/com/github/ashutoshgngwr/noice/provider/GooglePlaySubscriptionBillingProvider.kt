package com.github.ashutoshgngwr.noice.provider

import android.app.Activity
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.models.SubscriptionPlan
import com.github.ashutoshgngwr.noice.models.toDomainEntity
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SubscriptionFlowParams

/**
 * [SubscriptionBillingProvider] implementation that provides subscriptions using Google Play as the
 * billing provider.
 */
class GooglePlaySubscriptionBillingProvider(
  private val apiClient: NoiceApiClient,
  private val billingProvider: InAppBillingProvider,
) : SubscriptionBillingProvider {

  override suspend fun listPlans(currencyCode: String?): List<SubscriptionPlan> {
    return apiClient.subscriptions()
      .listPlans(SubscriptionPlan.PROVIDER_GOOGLE_PLAY, currencyCode)
      .toDomainEntity()
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
    val subscriptionId: Long = if (activePurchaseToken == null) {
      apiClient.subscriptions()
        .create(SubscriptionFlowParams(plan.id))
        .subscriptionId
    } else {
      activeSubscription.id
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
      obfuscatedAccountId = subscriptionId.toString(),
    )
  }

  override fun canUpgrade(s: Subscription): Boolean {
    return s.isActive && s.plan.provider == SubscriptionPlan.PROVIDER_GOOGLE_PLAY
  }
}
