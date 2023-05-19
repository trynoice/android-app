package com.github.ashutoshgngwr.noice.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.Purchase
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.models.SubscriptionPlan
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SubscriptionFlowParams
import kotlinx.coroutines.CoroutineScope

/**
 * [SubscriptionBillingProvider] implementation that provides subscriptions using Google Play as the
 * billing provider.
 */
class GooglePlaySubscriptionBillingProvider(
  private val apiClient: NoiceApiClient,
  private val billingProvider: GooglePlayBillingProvider,
  appDb: AppDatabase,
  defaultScope: CoroutineScope,
  appDispatchers: AppDispatchers,
) : SubscriptionBillingProvider(appDb, defaultScope, appDispatchers), GooglePlayPurchaseListener {

  init {
    billingProvider.addPurchaseListener(this)
  }

  override fun getId(): String = SubscriptionPlan.PROVIDER_GOOGLE_PLAY

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

    try {
      // we have set-up the Google Play subscription products such that each subscription has a
      // single base plan with at-most two offers (including the base plan without an offer). It may
      // have multiple pricing phases and the best way to find the the correct offer is to look for
      // an offer with maximum number of pricing phases. The offer with most number of pricing
      // phases should be the most economical offer.
      val details = billingProvider.queryDetails(ProductType.SUBS, listOf(sku))
      val offerToken = details.firstOrNull()
        ?.subscriptionOfferDetails
        ?.maxByOrNull { it.pricingPhases.pricingPhaseList.size }
        ?.offerToken

      billingProvider.purchase(
        activity,
        details.first(),
        subscriptionOfferToken = offerToken,
        oldPurchaseToken = activePurchaseToken,
        obfuscatedAccountId = subscriptionId.toString(),
      )
    } catch (e: GooglePlayBillingProviderException) {
      throw SubscriptionBillingProviderException("failed to launch google play billing flow", e)
    }
  }

  override fun isUpgradeable(s: Subscription): Boolean {
    return s.isActive && s.plan.provider == SubscriptionPlan.PROVIDER_GOOGLE_PLAY
  }

  override fun onInAppPurchasePending(purchase: Purchase): Boolean {
    val subscriptionId = purchase.extractSubscriptionId()
    if (subscriptionId != null) {
      notifyPurchase(subscriptionId, true)
      return true
    }
    return false
  }

  override fun onInAppPurchaseComplete(purchase: Purchase): Boolean {
    val subscriptionId = purchase.extractSubscriptionId()
    if (subscriptionId != null) {
      notifyPurchase(subscriptionId, false)
      return true
    }
    return false
  }

  private fun Purchase.extractSubscriptionId(): Long? {
    return accountIdentifiers?.obfuscatedAccountId?.toLongOrNull()
  }
}
