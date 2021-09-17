package com.github.ashutoshgngwr.noice.provider

import android.app.Activity
import android.content.Context

/**
 * [BillingProvider] is an abstract declaration of Google Play Billing APIs used by the app. This
 * interface abstracts concrete implementations and thus allowing free flavored builds to be
 * compiled without adding Google Play Billing dependencies.
 *
 * [BillingProvider] is implemented as a Singleton. It implies that both [init] and [close] must be
 * called in successions, i.e. an [init] call must not be followed by another [init] call.
 *
 * Typically for a single [Activity][android.app.Activity] application, [init] should be called in
 * [Activity.onCreate][android.app.Activity.onCreate], and [close] should be called in
 * [Activity.onDestroy][android.app.Activity.onDestroy]. All other components in this application
 * should use [BillingProvider] as is (without calling [init] and [close]).
 */
interface BillingProvider {

  /**
   * Initialises billing provider internals. It must be called before invoking any other
   * [BillingProvider] API.
   */
  fun init(context: Context, listener: PurchaseListener?)

  /**
   * Closes connection to the billing service. It must be called after a client is finished using
   * the [BillingProvider] API.
   */
  fun close()

  /**
   * Query details for given [skus].
   */
  @Throws(QueryDetailsException::class)
  suspend fun queryDetails(type: SkuType, skus: List<String>): List<SkuDetails>

  /**
   * Starts purchase flow for the given [sku].
   */
  fun purchase(activity: Activity, sku: SkuDetails): Boolean

  /**
   * Consumes all SKUs in the given order.
   */
  fun consumePurchase(orderId: String)

  /**
   * Listener for new purchases.
   */
  interface PurchaseListener {

    /**
     * Invoked when a new purchase is made while its payment is delayed and order is in pending state.
     */
    fun onPending(skus: List<String>)

    /**
     * Invoked when a (old or new) purchase's payment is processed and order is in purchased state.
     */
    fun onComplete(skus: List<String>, orderId: String)
  }

  data class SkuDetails(val price: String, val priceAmountMicros: Long, val originalJSON: String)

  enum class SkuType(val value: String) {
    INAPP("inapp"),
    SUBS("subs"),
  }

  class QueryDetailsException(msg: String) : Exception(msg)
}

/**
 * A no-op billing provider for clients that don't have Google Mobile Services installed.
 */
object DummyBillingProvider : BillingProvider {
  override fun init(context: Context, listener: BillingProvider.PurchaseListener?) = Unit
  override fun close() = Unit

  override suspend fun queryDetails(
    type: BillingProvider.SkuType,
    skus: List<String>
  ): List<BillingProvider.SkuDetails> {
    throw BillingProvider.QueryDetailsException("dummy billing provider doesn't implement query details")
  }

  override fun purchase(activity: Activity, sku: BillingProvider.SkuDetails): Boolean = false
  override fun consumePurchase(orderId: String) = Unit
}
