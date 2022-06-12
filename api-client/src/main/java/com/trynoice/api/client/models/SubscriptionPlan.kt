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
 * @param provider the provider of the subscription plan. It must be one of
 * [SubscriptionPlan.PROVIDER_GOOGLE_PLAY], [SubscriptionPlan.PROVIDER_STRIPE] or
 * [SubscriptionPlan.PROVIDER_GIFT_CARD].
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
  val provider: String,

  @Expose
  val trialPeriodDays: Int,
) : Serializable {

  /**
   * A formatted string representing total price of this plan.
   */
  val totalPrice get(): String = INR_FORMATTER.format(priceInIndianPaise / 100)

  /**
   * A formatted string representing monthly price of this plan.
   */
  val monthlyPrice
    get(): String? =
      if (billingPeriodMonths < 1) null
      else INR_FORMATTER.format(priceInIndianPaise / (billingPeriodMonths * 100))

  companion object {
    const val PROVIDER_GOOGLE_PLAY = "google_play"
    const val PROVIDER_STRIPE = "stripe"
    const val PROVIDER_GIFT_CARD = "gift_card"

    private val INR_FORMATTER = NumberFormat.getCurrencyInstance().apply {
      currency = Currency.getInstance("INR")
      minimumFractionDigits = 0
    }
  }
}
