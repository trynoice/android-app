package com.github.ashutoshgngwr.noice.provider

import android.content.Context

/**
 * [DummyBillingProvider] provides a no-op Google Play Billing API for F-Droid flavored builds to
 * compile without non-free dependencies.
 */
object DummyBillingProvider : BillingProvider {
  override fun init(context: Context, listener: BillingProvider.PurchaseListener?) = Unit
  override fun close() = Unit
  override fun consumePurchase(orderId: String) = Unit
}
