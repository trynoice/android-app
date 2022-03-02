package com.github.ashutoshgngwr.noice.provider

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
   * Query details of the given [skus].
   *
   * @return List of [InAppBillingProvider.SkuDetails] for provided [skus].
   * @throws InAppBillingProviderException when the query fails.
   */
  suspend fun queryDetails(type: SkuType, skus: List<String>): List<SkuDetails>

  /**
   * Starts purchase flow for the given [sku].
   *
   * @throws InAppBillingProviderException on failing to launch the billing flow.
   */
  fun purchase(activity: Activity, sku: SkuDetails)

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
   * Declares constants used by Google IAB to distinguish in-app and subscriptions SKU types.
   */
  enum class SkuType(val value: String) : Serializable {
    INAPP("inapp"),
    SUBS("subs"),
  }

  /**
   * a wrapper for the SkuDetails object from Google IAB.
   */
  data class SkuDetails(
    val price: String,
    val priceAmountMicros: Long,
    val originalJSON: String,
  ) : Serializable

  /**
   * a wrapper for the Purchase object from Google IAB.
   */
  data class Purchase(
    val skus: List<String>,
    val purchaseToken: String,
    val purchaseState: Int,
    val obfuscatedProfileId: String?,
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
    type: InAppBillingProvider.SkuType,
    skus: List<String>
  ): List<InAppBillingProvider.SkuDetails> = emptyList()

  override fun purchase(activity: Activity, sku: InAppBillingProvider.SkuDetails) = Unit
  override suspend fun acknowledgePurchase(purchase: InAppBillingProvider.Purchase) = Unit
  override suspend fun consumePurchase(purchase: InAppBillingProvider.Purchase) = Unit
}
