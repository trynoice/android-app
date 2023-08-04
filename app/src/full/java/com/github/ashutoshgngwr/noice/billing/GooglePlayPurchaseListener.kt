package com.github.ashutoshgngwr.noice.billing

import com.android.billingclient.api.Purchase

interface GooglePlayPurchaseListener {

  /**
   * Invoked when a new purchase is made but its payment is delayed and the order is in pending
   * state.
   *
   * @return `true` if the event is consumed, `false` otherwise.
   */
  fun onInAppPurchasePending(purchase: Purchase): Boolean

  /**
   * Invoked when a (old or new) purchase's payment is processed and the order is in purchased
   * state.
   *
   * @return `true` if the event is consumed, `false` otherwise.
   */
  fun onInAppPurchaseComplete(purchase: Purchase): Boolean
}
