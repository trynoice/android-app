package com.trynoice.api.client.models

import com.google.gson.annotations.Expose

/**
 * Plan associated with this subscription purchases.
 *
 * @param billingPeriodMonths number of months included in a single billing period, e.g. 1 or 3
 * @param id id of the subscription plan
 * @param priceInr currency formatted string showing plan's price in INR, e.g. '₹225'
 * @param provider provider of the subscription plan, e.g. google_play or stripe
 * @param trialPeriodDays number of days included as the trial period with the plan
 */
data class SubscriptionPlan(

  @Expose
  val billingPeriodMonths: Int,

  @Expose
  val id: Int,

  @Expose
  val priceInr: String,

  @Expose
  val provider: String,

  @Expose
  val trialPeriodDays: Int
)
