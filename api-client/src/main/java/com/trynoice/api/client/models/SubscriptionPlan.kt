package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable
import java.text.NumberFormat
import java.util.*

/**
 * Represents a subscription plan that users can subscribe. All subscription purchases are linked to
 * a subscription plan.
 *
 * @param billingPeriodMonths number of months included in a single billing period, e.g. 1 or 3
 * @param id id of the subscription plan
 * @param googlePlaySubscriptionId Google Play assigned id of the subscription plan
 * @param priceInIndianPaise price of the plan in Indian Paise (INR * 100)
 * @param priceInRequestedCurrency an optional converted price if currency was provided in the
 * request parameters. It may be absent despite specifying the currency in the request parameters.
 * @param provider the provider of the subscription plan. It must be one of
 * [SubscriptionPlan.PROVIDER_GOOGLE_PLAY], [SubscriptionPlan.PROVIDER_STRIPE] or
 * [SubscriptionPlan.PROVIDER_GIFT_CARD].
 * @param requestedCurrencyCode an optional currency code for the priceInRequestedCurrency. It is
 * only present if [priceInRequestedCurrency] is also present.
 * @param trialPeriodDays number of days included as the trial period with the plan
 */
data class SubscriptionPlan(

  @Expose
  val billingPeriodMonths: Int,

  @Expose
  val id: Int,

  @Expose
  val googlePlaySubscriptionId: String?,

  @Expose
  val priceInIndianPaise: Int,

  @Expose
  val priceInRequestedCurrency: Double? = null,

  @Expose
  val provider: String,

  @Expose
  val requestedCurrencyCode: String? = null,

  @Expose
  val trialPeriodDays: Int,
) : Serializable {

  /**
   * A formatted string representing total price of this plan.
   */
  val totalPrice
    get(): String = if (priceInRequestedCurrency != null && requestedCurrencyCode != null) {
      formatPrice(priceInRequestedCurrency, requestedCurrencyCode)
    } else {
      formatPrice(priceInIndianPaise / 100.0, "INR")
    }

  /**
   * A formatted string representing monthly price of this plan.
   */
  val monthlyPrice
    get(): String? = when {
      billingPeriodMonths < 1 -> null
      priceInRequestedCurrency != null && requestedCurrencyCode != null -> {
        formatPrice(priceInRequestedCurrency / billingPeriodMonths, requestedCurrencyCode)
      }
      else -> formatPrice(priceInIndianPaise / (billingPeriodMonths * 100.0), "INR")
    }

  private fun formatPrice(price: Double, currencyCode: String): String {
    return NumberFormat.getCurrencyInstance()
      .apply {
        currency = Currency.getInstance(currencyCode)
        minimumFractionDigits = if (price % 1 == 0.0) 0 else minimumFractionDigits
      }
      .format(price)
  }

  companion object {
    const val PROVIDER_GOOGLE_PLAY = "google_play"
    const val PROVIDER_STRIPE = "stripe"
    const val PROVIDER_GIFT_CARD = "gift_card"
  }
}
