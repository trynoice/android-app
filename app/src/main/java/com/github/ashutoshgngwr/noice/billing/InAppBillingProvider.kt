package com.github.ashutoshgngwr.noice.billing

import android.app.Activity
import java.io.Serializable

/**
 * [InAppBillingProvider] is an abstract declaration of Google Play Billing APIs used by the app.
 * This interface abstracts concrete implementations and thus allowing free flavored builds to be
 * compiled without adding Google Play Billing dependencies.
 */
interface InAppBillingProvider {

  /**
   * Sets a purchase listener. The clients must explicitly remove their listeners by setting it to
   * `null` when they are done.
   */
  fun setPurchaseListener(listener: PurchaseListener?)

  /**
   * Query details of the given [productIds].
   *
   * @return a list of [ProductDetails] for provided [productIds].
   * @throws InAppBillingProviderException when the query fails.
   */
  suspend fun queryDetails(type: ProductType, productIds: List<String>): List<ProductDetails>

  /**
   * Starts purchase flow for the given product [details].
   *
   * @param activity current activity context to launch the billing flow.
   * @param details [ProductDetails] of the in-app product or subscription to purchase.
   * @param subscriptionOfferToken selected offer token for subscription products.
   * @param oldPurchaseToken purchase token of the active subscription to launch an upgrade flow.
   * @param obfuscatedAccountId an identifier to identify purchase on the server-side.
   * @throws InAppBillingProviderException on failing to launch the billing flow.
   */
  fun purchase(
    activity: Activity,
    details: ProductDetails,
    subscriptionOfferToken: String? = null,
    oldPurchaseToken: String? = null,
    obfuscatedAccountId: String? = null,
  )

  /**
   * Acknowledges a given purchase.
   *
   * @throws InAppBillingProviderException on failing to acknowledge the purchase.
   */
  suspend fun acknowledgePurchase(purchase: Purchase)

  /**
   * Consumes a given purchase.
   *
   * @throws InAppBillingProviderException on failing to consume the purchase.
   */
  suspend fun consumePurchase(purchase: Purchase)

  /**
   * Listener for new purchases.
   */
  interface PurchaseListener {

    /**
     * Invoked when a new purchase is made but its payment is delayed and order is in pending state.
     */
    fun onPending(purchase: Purchase)

    /**
     * Invoked when a (old or new) purchase's payment is processed and order is in purchased state.
     */
    fun onComplete(purchase: Purchase)
  }

  /**
   * Declares constants used by Google IAB to distinguish in-app and subscriptions product types.
   */
  enum class ProductType(val value: String) : Serializable {
    INAPP("inapp"),
    SUBS("subs"),
  }

  /**
   * a wrapper for the ProductDetails object from Google IAB.
   */
  data class ProductDetails(
    val oneTimeOfferDetails: OneTimeOfferDetails?,
    val subscriptionOfferDetails: List<SubscriptionOfferDetails>?,
    val rawObject: Any,
  ) : Serializable

  data class OneTimeOfferDetails(
    val price: String,
    val priceAmountMicros: Long,
  ) : Serializable

  data class SubscriptionOfferDetails(
    val offerToken: String,
    val pricingPhases: List<SubscriptionPricingPhase>,
  ) : Serializable

  data class SubscriptionPricingPhase(
    val price: String,
    val priceAmountMicros: Long,
    val billingPeriod: String,
  ) : Serializable

  /**
   * a wrapper for the Purchase object from Google IAB.
   */
  data class Purchase(
    val productIds: List<String>,
    val purchaseToken: String,
    val purchaseState: Int,
    val obfuscatedAccountId: String?,
    val originalJSON: String,
    val signature: String,
  ) : Serializable
}

/**
 * Thrown by various [InAppBillingProvider] operations.
 */
class InAppBillingProviderException(msg: String) : Exception(msg)

/**
 * A no-op billing provider for clients that don't have Google Mobile Services installed.
 */
object DummyInAppBillingProvider : InAppBillingProvider {
  override fun setPurchaseListener(listener: InAppBillingProvider.PurchaseListener?) = Unit
  override suspend fun queryDetails(
    type: InAppBillingProvider.ProductType,
    productIds: List<String>,
  ): List<InAppBillingProvider.ProductDetails> = emptyList()

  override fun purchase(
    activity: Activity,
    details: InAppBillingProvider.ProductDetails,
    subscriptionOfferToken: String?,
    oldPurchaseToken: String?,
    obfuscatedAccountId: String?,
  ) = Unit

  override suspend fun acknowledgePurchase(purchase: InAppBillingProvider.Purchase) = Unit
  override suspend fun consumePurchase(purchase: InAppBillingProvider.Purchase) = Unit
}
