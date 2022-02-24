package com.trynoice.api.client.models

import com.google.gson.annotations.Expose

/**
 * Represents a subscription plan that users can subscribe. All subscription purchases are linked to
 * a subscription plan.
 *
 * @param billingPeriodMonths number of months included in a single billing period, e.g. 1 or 3
 * @param id id of the subscription plan
 * @param googlePlaySubscriptionId Google Play assigned id of the subscription plan
 * @param priceInIndianPaise price of the plan in Indian Paise (INR * 100)
 * @param provider provider of the subscription plan, e.g. google_play or stripe
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
)
