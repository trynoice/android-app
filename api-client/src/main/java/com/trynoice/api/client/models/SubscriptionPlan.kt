package com.trynoice.api.client.models

/**
 * Represents a subscription plan that users can subscribe. All subscription purchases are linked to
 * a subscription plan.
 *
 * @property billingPeriodMonths number of months included in a single billing period, e.g. 1 or 3
 * @property id id of the subscription plan
 * @property googlePlaySubscriptionId Google Play assigned id of the subscription plan
 * @property priceInIndianPaise price of the plan in Indian Paise (INR * 100)
 * @property priceInRequestedCurrency an optional converted price if currency was provided in the
 * request parameters. It may be absent despite specifying the currency in the request parameters.
 * @property provider the provider of the subscription plan. It must be one of
 * [SubscriptionPlan.PROVIDER_GOOGLE_PLAY], [SubscriptionPlan.PROVIDER_STRIPE] or
 * [SubscriptionPlan.PROVIDER_GIFT_CARD].
 * @property requestedCurrencyCode an optional currency code for the priceInRequestedCurrency. It is
 * only present if [priceInRequestedCurrency] is also present.
 * @property trialPeriodDays number of days included as the trial period with the plan
 */
data class SubscriptionPlan(
  val billingPeriodMonths: Int,
  val id: Int,
  val googlePlaySubscriptionId: String?,
  val priceInIndianPaise: Int,
  val priceInRequestedCurrency: Double? = null,
  val provider: String,
  val requestedCurrencyCode: String? = null,
  val trialPeriodDays: Int,
) {

  companion object {
    const val PROVIDER_GOOGLE_PLAY = "google_play"
    const val PROVIDER_STRIPE = "stripe"
    const val PROVIDER_GIFT_CARD = "gift_card"
  }
}
