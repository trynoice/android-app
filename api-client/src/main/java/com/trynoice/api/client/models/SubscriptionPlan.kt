package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable

/**
 * Represents a subscription plan that users can subscribe. All subscription purchases are linked to
 * a subscription plan.
 *
 * @param billingPeriodMonths number of months included in a single billing period, e.g. 1 or 3
 * @param id id of the subscription plan
 * @param googlePlaySubscriptionId Google Play assigned id of the subscription plan
 * @param priceInIndianPaise price of the plan in Indian Paise (INR * 100)
 * @param provider the provider of the subscription plan. It must be one of
 * [SubscriptionPlan.PROVIDER_GOOGLE_PLAY] or [SubscriptionPlan.PROVIDER_STRIPE].
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

  companion object {
    const val PROVIDER_GOOGLE_PLAY = "google_play"
    const val PROVIDER_STRIPE = "stripe"
  }
}
